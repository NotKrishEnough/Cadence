slint::include_modules!();

use lofty::prelude::Accessor;
use std::sync::{Arc, Mutex};

#[derive(Clone)]
struct Track {
    title: String,
    artist: String,
    album: String,
    path: String,
}

struct Player {
    tracks: Vec<Track>,
    index: usize,
    playing: bool,
    sink: Option<rodio::Sink>,
    stream: Option<rodio::OutputStream>,
}

impl Player {
    fn new() -> Self {
        Self { tracks: Vec::new(), index: 0, playing: false, sink: None, stream: None }
    }

    fn scan_music(&mut self) {
        self.tracks.clear();
        for root in ["/storage/emulated/0/Music", "/sdcard/Music"] {
            if !std::path::Path::new(root).exists() { continue; }
            for entry in walkdir::WalkDir::new(root).into_iter().flatten() {
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

                self.tracks.push(Track { title, artist, album, path: path.to_string_lossy().to_string() });
            }
        }
        self.tracks.sort_by_key(|t| t.title.to_lowercase());
    }

    fn play_current(&mut self) {
        if self.tracks.is_empty() { return; }
        let Ok(stream) = rodio::OutputStreamBuilder::open_default_stream() else { return; };
        let Ok(file) = std::fs::File::open(&self.tracks[self.index].path) else { return; };
        let Ok(source) = rodio::Decoder::try_from(file) else { return; };

        let sink = rodio::Sink::connect_new(stream.mixer());
        sink.append(source);
        sink.play();
        self.stream = Some(stream);
        self.sink = Some(sink);
        self.playing = true;
    }

    fn toggle(&mut self) {
        if let Some(sink) = &self.sink {
            if self.playing { sink.pause(); self.playing = false; }
            else { sink.play(); self.playing = true; }
        } else {
            self.play_current();
        }
    }

    fn next(&mut self) {
        if self.tracks.is_empty() { return; }
        self.index = (self.index + 1) % self.tracks.len();
        self.sink = None;
        self.stream = None;
        self.play_current();
    }

    fn previous(&mut self) {
        if self.tracks.is_empty() { return; }
        self.index = if self.index == 0 { self.tracks.len() - 1 } else { self.index - 1 };
        self.sink = None;
        self.stream = None;
        self.play_current();
    }
}

fn update_ui(ui: &MainWindow, p: &Player) {
    let (title, artist, album) = p.tracks.get(p.index)
        .map(|t| (t.title.clone(), t.artist.clone(), t.album.clone()))
        .unwrap_or(("No music yet".into(), "Add songs to /Music".into(), "Cadence".into()));

    ui.set_track_title(title.into());
    ui.set_track_artist(artist.into());
    ui.set_album_title(album.into());
    ui.set_playing(p.playing);
    ui.set_library_count(p.tracks.len() as i32);
}

fn run_app() {
    let ui = MainWindow::new().unwrap();
    let player = Arc::new(Mutex::new(Player::new()));

    {
        let mut p = player.lock().unwrap();
        p.scan_music();
        update_ui(&ui, &p);
    }

    let p = player.clone();
    let weak = ui.as_weak();
    ui.on_play_pause(move || {
        if let Ok(mut p) = p.lock() {
            p.toggle();
            if let Some(ui) = weak.upgrade() { update_ui(&ui, &p); }
        }
    });

    let p = player.clone();
    let weak = ui.as_weak();
    ui.on_next(move || {
        if let Ok(mut p) = p.lock() {
            p.next();
            if let Some(ui) = weak.upgrade() { update_ui(&ui, &p); }
        }
    });

    let p = player.clone();
    let weak = ui.as_weak();
    ui.on_previous(move || {
        if let Ok(mut p) = p.lock() {
            p.previous();
            if let Some(ui) = weak.upgrade() { update_ui(&ui, &p); }
        }
    });

    let weak = ui.as_weak();
    ui.on_open_library(move || {
        if let Some(ui) = weak.upgrade() { ui.set_screen(1); }
    });

    let weak = ui.as_weak();
    ui.on_open_playlists(move || {
        if let Some(ui) = weak.upgrade() { ui.set_screen(2); }
    });

    let weak = ui.as_weak();
    ui.on_open_player(move || {
        if let Some(ui) = weak.upgrade() { ui.set_screen(0); }
    });

    let weak = ui.as_weak();
    ui.on_open_queue(move || {
        if let Some(ui) = weak.upgrade() { ui.set_screen(0); }
    });

    let p = player.clone();
    let weak = ui.as_weak();
    ui.on_rescan(move || {
        if let Ok(mut p) = p.lock() {
            p.scan_music();
            if let Some(ui) = weak.upgrade() { update_ui(&ui, &p); }
        }
    });

    ui.run().unwrap();
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
fn android_main(app: slint::android::AndroidApp) {
    slint::android::init(app).unwrap();
    run_app();
}

#[cfg(not(target_os = "android"))]
fn main() {
    run_app();
}
