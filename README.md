![LangTrainer](./assets/banner.png)
[![License Apache 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-green.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Java 17+](https://img.shields.io/badge/java-17%2b-green.svg)](https://bell-sw.com/pages/downloads/)

[![Arthur's Acres Animal Sanctuary — donate](assets/arthur_sanctuary_banner.png)](https://www.arthursacresanimalsanctuary.org/donate)

# LangTrainer

LangTrainer is a small Java desktop application for practicing dialogs, words, and phrases in
foreign languages. It provides several training modes over JSON-based language resources, including
dialog practice, flying word games, crossword generation, phrase building with bricks, paired-bar
matching, and image association exercises.

The application is intended for offline personal language training. Lessons are stored as readable
JSON files, so new vocabularies, dialogs, phrase lists, and image resources can be added or edited
without a server. Bundled lessons can be supplemented by opening local JSON files or synchronizing
external lessons from the application.

## Features

- Dialog-based translation practice with tolerant checking for punctuation, case, and small typing mistakes.
- Fly game for timed word recall.
- Crossword mode for single-word vocabulary resources.
- Bricks mode for rebuilding phrases from shuffled word parts.
- Bars mode for matching paired words or sentences by dragging rows into alignment.
- Images mode for matching text labels to embedded pictures.
- Shared resource selector with bundled lessons, local JSON files, and external lesson synchronization.
- Built-in editor for language resource JSON files, including module filters, right-to-left language flags, input
  equivalence rules, and embedded line images.
- Virtual keyboards and input equivalence rules for keyboard-friendly typing of accented or language-specific letters.
- Right-to-left support for compatible resources, including Hebrew-oriented typing and layout behavior.

## Application

The application is written in [Java](https://en.wikipedia.org/wiki/Java_%28programming_language%29) and
requires [Java 17+](https://bell-sw.com/pages/downloads/) or later to be installed on your computer. For convenience,
there are also build versions that come bundled with a compatible Java runtime, so you don't have to install Java
separately if you don’t want to.

| OS                                             | Download link                                                                                                                                       | 
|------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| ![Windows](assets/icons/win64x64.png)          | __[Archive with JRE for Windows amd64](https://github.com/raydac/langtrainer/releases/download/1.1.0/langtrainer-app-1.1.0-windows-jdk-amd64.zip)__ |
| ![Windows](assets/icons/win64x64.png)          | __[Archive without JRE for Windows](https://github.com/raydac/langtrainer/releases/download/1.1.0/langtrainer-app-1.1.0.exe)__                      |
| ![macOS](assets/icons/macos64x64.png)          | __[Archive with JRE for macOS amd64](https://github.com/raydac/langtrainer/releases/download/1.1.0/langtrainer-app-1.1.0-macos-jdk-amd64.zip)__     |
| ![macOS Arm64](assets/icons/macosarm64x64.png) | __[Archive with JRE for macOS arm64](https://github.com/raydac/langtrainer/releases/download/1.1.0/langtrainer-app-1.1.0-macos-jdk-aarch64.zip)__   |
| ![macOS](assets/icons/macos64x64.png)          | __[DMG package for macOS (no JRE)](https://github.com/raydac/langtrainer/releases/download/1.1.0/langtrainer_1.1.0.dmg)__                           |
| ![Linux](assets/icons/linux64x64.png)          | __[Archive with JRE for Linux amd64](https://github.com/raydac/langtrainer/releases/download/1.1.0/langtrainer-app-1.1.0-linux-jdk-amd64.tar.gz)__  |
| ![Linux](assets/icons/appimage64x64.png)       | __[AppImage for Linux amd64](https://github.com/raydac/langtrainer/releases/download/1.1.0/langtrainer-app-1.1.0-x86_64.AppImage)__                 |
| ![Java](assets/icons/java64x64.png)            | __[Cross-platform JAR file](https://github.com/raydac/langtrainer/releases/download/1.1.0/langtrainer-app-1.1.0.jar)__                              | 

__[Full set of latest pre-built applications](https://github.com/raydac/langtrainer/releases/latest)__

## Resource format

Lessons use a compact JSON format with two language columns, optional module visibility rules, optional right-to-left
language flags, optional input equivalence rules, and optional embedded PNG/JPG/SVG images per line. See
[`langtrainer-app/docs/JSON_format.txt`](langtrainer-app/docs/JSON_format.txt) for the full format and module-specific
notes in [`langtrainer-app/docs`](langtrainer-app/docs).

## Build from source

This project is built with Maven and targets Java 17:

```bash
mvn -pl langtrainer-app -am package
```

To start the application from source:

```bash
mvn -pl langtrainer-app -am exec:java
```
