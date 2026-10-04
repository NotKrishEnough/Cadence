slint::include_modules!();

use lofty::prelude::{Accessor, TaggedFileExt};
use serde::{Deserialize, Serialize};
use slint::{Image, ModelRc, VecModel};
use std::path::{Path, PathBuf};
use std::rc::Rc;
use std::sync::{Arc, Mutex};

#[cfg(target_os = "android")]
use jni::{objects::{JObject, JValue}, JavaVM};

#[derive(Clone)]
struct Track { title: String, artist: String, album: String, path: String, artwork: Option<PathBuf> }

#[derive(Default, Serialize, Deserialize)]
struct PlaylistStore { favorites: Vec<String> }

struct Player {
    tracks: Vec<Track>, index: usize, queue: Vec<usize>, queue_pos: usize,
    playing: bool, sink: Option<rodio::Sink>, stream: Option<rodio::OutputStream>,
    favorites: Vec<String>,
}

#[cfg(target_os = "android")]
fn with_android_env<R>(f: impl for<'a> FnOnce(&mut jni::Env<'a>, ndk_context::AndroidContext) -> R) -> Option<R> {
    let ctx = ndk_context::android_context();
    let vm = unsafe { JavaVM::from_raw(ctx.vm().cast()) };
    vm.attach_current_thread(|env| Ok::<_, jni::errors::Error>(f(env, ctx))).ok()
}

#[cfg(target_os = "android")]
fn android_service_call(method: &str) -> bool {
    with_android_env(|env, ctx| {
        let Ok(class) = env.find_class("com/notkrishenough/cadence/PlaybackService") else { return false; };
        let context = unsafe { JObject::from_raw(env, ctx.context().cast()) };
        let result = env.call_static_method(
            class, method, "(Landroid/content/Context;)V",
            &[JValue::Object(&context)],
        ).is_ok();
        std::mem::forget(context);
        result
    }).unwrap_or(false)
}

#[cfg(target_os = "android")]
fn android_start_playback(path: &str, title: &str, artist: &str) -> bool {
    with_android_env(|env, ctx| {
        let Ok(class) = env.find_class("com/notkrishenough/cadence/PlaybackService") else { return false; };
        let context = unsafe { JObject::from_raw(env, ctx.context().cast()) };
        let Ok(path) = env.new_string(path) else { std::mem::forget(context); return false; };
        let Ok(title) = env.new_string(title) else { std::mem::forget(context); return false; };
        let Ok(artist) = env.new_string(artist) else { std::mem::forget(context); return false; };
        let result = env.call_static_method(
            class,
            "startPlayback",
            "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
            &[
                JValue::Object(&context),
                JValue::Object(&path),
                JValue::Object(&title),
                JValue::Object(&artist),
            ],
        ).is_ok();
        std::mem::forget(context);
        result
    }).unwrap_or(false)
}

impl Player {
    fn new() -> Self {
        Self { tracks: Vec::new(), index: 0, queue: Vec::new(), queue_pos: 0, playing: false,
            sink: None, stream: None, favorites: Self::load_store().favorites }
    }

    fn store_path() -> PathBuf {
        PathBuf::from("/storage/emulated/0/Android/data/com.notkrishenough.cadence/files/playlists.json")
    }
    fn load_store() -> PlaylistStore {
        std::fs::read_to_string(Self::store_path()).ok()
            .and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default()
    }
    fn save_store(&self) {
        let path = Self::store_path();
        if let Some(parent) = path.parent() { let _ = std::fs::create_dir_all(parent); }
        if let Ok(json) = serde_json::to_string_pretty(&PlaylistStore { favorites: self.favorites.clone() }) {
            let _ = std::fs::write(path, json);
        }
    }

    fn scan_music(&mut self) {
        self.tracks.clear();
        for root in ["/storage/emulated/0/Music", "/sdcard/Music"] {
            let root_path = Path::new(root);
            if !root_path.exists() { continue; }
            for entry in walkdir::WalkDir::new(root_path).into_iter().flatten() {
                let path = entry.path();
                if !path.is_file() { continue; }
                let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("").to_ascii_lowercase();
                if !matches!(ext.as_str(), "mp3" | "flac" | "m4a" | "aac" | "ogg" | "wav") { continue; }

                let mut title = path.file_stem().and_then(|s| s.to_str()).unwrap_or("Unknown").to_string();
                let mut artist = "Unknown Artist".to_string();
                let mut album = "Unknown Album".to_string();
                if let Ok(tagged) = lofty::read_from_path(path) {
                    if let Some(tag) = tagged.primary_tag() {
                        if let Some(v) = tag.title() { title = v.to_string(); }
                        if let Some(v) = tag.artist() { artist = v.to_string(); }
                        if let Some(v) = tag.album() { album = v.to_string(); }
                    }
                }
                self.tracks.push(Track { title, artist, album, path: path.to_string_lossy().to_string(), artwork: Self::find_artwork(path) });
            }
        }
        self.tracks.sort_by_key(|t| t.title.to_lowercase());
        self.index = self.index.min(self.tracks.len().saturating_sub(1));
        self.rebuild_queue();
    }

    fn find_artwork(song: &Path) -> Option<PathBuf> {
        let dir = song.parent()?;
        for name in ["cover.jpg","cover.jpeg","cover.png","folder.jpg","folder.jpeg","folder.png","album.jpg","album.jpeg","album.png"] {
            let p = dir.join(name);
            if p.is_file() { return Some(p); }
        }
        let stem = song.file_stem()?.to_string_lossy().to_lowercase();
        std::fs::read_dir(dir).ok()?.flatten().map(|e| e.path()).find(|p| {
            let ext = p.extension().and_then(|e| e.to_str()).unwrap_or("").to_ascii_lowercase();
            let n = p.file_stem().and_then(|s| s.to_str()).unwrap_or("").to_lowercase();
            matches!(ext.as_str(), "jpg" | "jpeg" | "png") && (n == stem || n.contains("artwork") || n.contains("front"))
        })
    }

    fn play_current(&mut self) {
        if self.tracks.is_empty() { return; }
        self.sink = None; self.stream = None;
        #[cfg(target_os = "android")]
        {
            let track = &self.tracks[self.index];
            if android_start_playback(&track.path, &track.title, &track.artist) {
                self.playing = true;
                return;
            }
        }

        let Ok(stream) = rodio::OutputStreamBuilder::open_default_stream() else { return; };
        let Ok(file) = std::fs::File::open(&self.tracks[self.index].path) else { return; };
        let Ok(source) = rodio::Decoder::try_from(file) else { return; };
        let sink = rodio::Sink::connect_new(stream.mixer());
        sink.append(source); sink.play();
        self.stream = Some(stream); self.sink = Some(sink); self.playing = true;
    }
    fn toggle(&mut self) {
        #[cfg(target_os = "android")]
        if self.sink.is_none() {
            if self.playing {
                if android_service_call("pausePlayback") { self.playing = false; return; }
            } else if android_service_call("resumePlayback") {
                self.playing = true;
                return;
            }
        }

        if let Some(sink) = &self.sink {
            if self.playing { sink.pause(); self.playing = false; } else { sink.play(); self.playing = true; }
        } else { self.play_current(); }
    }
    fn select_path(&mut self, path: &str) {
        if let Some(index) = self.tracks.iter().position(|t| t.path == path) {
            self.index = index;
            self.queue_pos = self.queue.iter().position(|i| *i == index).unwrap_or(0);
            self.play_current();
        }
    }
    fn next(&mut self) {
        if self.tracks.is_empty() { return; }
        if self.queue.is_empty() { self.index = (self.index + 1) % self.tracks.len(); }
        else { self.queue_pos = (self.queue_pos + 1) % self.queue.len(); self.index = self.queue[self.queue_pos]; }
        self.play_current();
    }
    fn previous(&mut self) {
        if self.tracks.is_empty() { return; }
        if self.queue.is_empty() { self.index = if self.index == 0 { self.tracks.len()-1 } else { self.index-1 }; }
        else { self.queue_pos = if self.queue_pos == 0 { self.queue.len()-1 } else { self.queue_pos-1 }; self.index = self.queue[self.queue_pos]; }
        self.play_current();
    }
    fn toggle_favorite(&mut self) {
        if let Some(track) = self.tracks.get(self.index) {
            if let Some(pos) = self.favorites.iter().position(|p| p == &track.path) { self.favorites.remove(pos); }
            else { self.favorites.push(track.path.clone()); }
            self.save_store();
        }
    }
    fn is_favorite(&self) -> bool {
        self.tracks.get(self.index).map(|t| self.favorites.iter().any(|p| p == &t.path)).unwrap_or(false)
    }
    fn rebuild_queue(&mut self) {
        self.queue = (0..self.tracks.len()).collect();
        self.queue_pos = self.queue.iter().position(|i| *i == self.index).unwrap_or(0);
    }
}

fn make_song_model(p: &Player) -> ModelRc<Song> {
    Rc::new(VecModel::from(p.tracks.iter().map(|t| Song {
        title: t.title.clone().into(), artist: t.artist.clone().into(), album: t.album.clone().into(),
        path: t.path.clone().into(), artwork: t.artwork.as_ref().and_then(|x| Image::load_from_path(x).ok()).unwrap_or_default()
    }).collect::<Vec<_>>())).into()
}
fn make_queue_model(p: &Player) -> ModelRc<QueueSong> {
    Rc::new(VecModel::from(p.queue.iter().filter_map(|i| p.tracks.get(*i)).map(|t| QueueSong {
        title: t.title.clone().into(), artist: t.artist.clone().into()
    }).collect::<Vec<_>>())).into()
}
fn update_ui(ui: &MainWindow, p: &Player) {
    let (title, artist, album, artwork) = p.tracks.get(p.index).map(|t| (
        t.title.clone(), t.artist.clone(), t.album.clone(),
        t.artwork.as_ref().and_then(|x| Image::load_from_path(x).ok()).unwrap_or_default()
    )).unwrap_or(("No music yet".into(), "Add songs to /Music".into(), "Cadence".into(), Image::default()));
    ui.set_track_title(title.into()); ui.set_track_artist(artist.into()); ui.set_album_title(album.into());
    ui.set_current_artwork(artwork); ui.set_playing(p.playing); ui.set_favorite(p.is_favorite());
    ui.set_library_count(p.tracks.len() as i32); ui.set_playlist_count(p.favorites.len() as i32);
    ui.set_library_songs(make_song_model(p)); ui.set_queue_songs(make_queue_model(p)); ui.set_queue_count(p.queue.len() as i32);
}

fn run_app() {
    let ui = MainWindow::new().unwrap();
    let player = Arc::new(Mutex::new(Player::new()));
    { let mut p = player.lock().unwrap(); p.scan_music(); update_ui(&ui, &p); }

    let p = player.clone(); let weak = ui.as_weak();
    ui.on_play_pause(move || { if let Ok(mut p) = p.lock() { p.toggle(); if let Some(ui)=weak.upgrade(){update_ui(&ui,&p);} } });
    let p = player.clone(); let weak = ui.as_weak();
    ui.on_next(move || { if let Ok(mut p) = p.lock() { p.next(); if let Some(ui)=weak.upgrade(){update_ui(&ui,&p);} } });
    let p = player.clone(); let weak = ui.as_weak();
    ui.on_previous(move || { if let Ok(mut p) = p.lock() { p.previous(); if let Some(ui)=weak.upgrade(){update_ui(&ui,&p);} } });
    let p = player.clone(); let weak = ui.as_weak();
    ui.on_select_song(move |index| { if let Ok(mut p)=p.lock(){p.select_path(index.as_str());if let Some(ui)=weak.upgrade(){update_ui(&ui,&p);}} });
    let p = player.clone(); let weak = ui.as_weak();
    ui.on_toggle_favorite(move || { if let Ok(mut p)=p.lock(){p.toggle_favorite();if let Some(ui)=weak.upgrade(){update_ui(&ui,&p);}} });
    let p = player.clone(); let weak = ui.as_weak();
    ui.on_rescan(move || { if let Ok(mut p)=p.lock(){p.scan_music();if let Some(ui)=weak.upgrade(){update_ui(&ui,&p);}} });
    let weak = ui.as_weak(); ui.on_open_library(move || {if let Some(ui)=weak.upgrade(){ui.set_screen(1);}});
    let weak = ui.as_weak(); ui.on_open_playlists(move || {if let Some(ui)=weak.upgrade(){ui.set_screen(2);}});
    let weak = ui.as_weak(); ui.on_open_player(move || {if let Some(ui)=weak.upgrade(){ui.set_screen(0);}});
    let weak = ui.as_weak(); ui.on_open_queue(move || {if let Some(ui)=weak.upgrade(){ui.set_screen(3);}});
    ui.run().unwrap();
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
fn android_main(app: slint::android::AndroidApp) { slint::android::init(app).unwrap(); run_app(); }
#[cfg(not(target_os = "android"))]
fn main() { run_app(); }
