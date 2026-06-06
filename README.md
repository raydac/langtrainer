![LangTrainer](./assets/banner.png)
[![License Apache 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-green.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Java 17+](https://img.shields.io/badge/java-21%2b-green.svg)](https://bell-sw.com/pages/downloads/#jdk-21-lts)   
[![Arthur's Acres Animal Sanctuary — donate](assets/arthur_sanctuary_banner.png)](https://www.arthursacresanimalsanctuary.org/donate)

# LangTrainer

LangTrainer is a small Java desktop application for practicing dialogs, words, and phrases in
foreign languages. It provides several training modes over JSON-based language resources, including
dialog practice, flying word games, crossword generation, and phrase building with bricks.

The application is intended for offline personal language training. Lessons are stored as readable
JSON files, so new vocabularies, dialogs, and phrase lists can be added or edited without a server.

## Features

- Dialog-based translation practice.
- Word and phrase training games.
- Crossword mode for single-word vocabulary resources.
- Bricks mode for rebuilding phrases from word parts.
- Built-in editor for language resource JSON files.
- Input equivalence rules for keyboard-friendly typing of accented or language-specific letters.

## Application

The application is written in [Java](https://en.wikipedia.org/wiki/Java_%28programming_language%29) and
requires [Java 17+](https://bell-sw.com/pages/downloads/#jdk-21-lts) or later to be installed on your computer. For
convenience,
there are also build versions that come bundled with a compatible Java runtime, so you don't have to install Java
separately if you don’t want to.

| OS                                             | Download link                                                                                                                                       | 
|------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| ![Windows](assets/icons/win64x64.png)          | __[Archive with JRE for Windows amd64](https://github.com/raydac/langtrainer/releases/download/1.0.0/langtrainer-app-1.0.0-windows-jdk-amd64.zip)__ |
| ![Windows](assets/icons/win64x64.png)          | __[Archive without JRE for Windows](https://github.com/raydac/langtrainer/releases/download/1.0.0/langtrainer-app-1.0.0.exe)__                      |
| ![macOS](assets/icons/macos64x64.png)          | __[Archive with JRE for macOS amd64](https://github.com/raydac/langtrainer/releases/download/1.0.0/langtrainer-app-1.0.0-macos-jdk-amd64.zip)__     |
| ![macOS Arm64](assets/icons/macosarm64x64.png) | __[Archive with JRE for macOS arm64](https://github.com/raydac/langtrainer/releases/download/1.0.0/langtrainer-app-1.0.0-macos-jdk-aarch64.zip)__   |
| ![macOS](assets/icons/macos64x64.png)          | __[DMG package for macOS (no JRE)](https://github.com/raydac/langtrainer/releases/download/1.0.0/langtrainer_1.0.0.dmg)__                           |
| ![Linux](assets/icons/linux64x64.png)          | __[Archive with JRE for Linux amd64](https://github.com/raydac/langtrainer/releases/download/1.0.0/langtrainer-app-1.0.0-linux-jdk-amd64.tar.gz)__  |
| ![Linux](assets/icons/appimage64x64.png)       | __[AppImage for Linux amd64](https://github.com/raydac/langtrainer/releases/download/1.0.0/langtrainer-app-1.0.0-x86_64.AppImage)__                 |
| ![Java](assets/icons/java64x64.png)            | __[Cross-platform JAR file](https://github.com/raydac/langtrainer/releases/download/1.0.0/langtrainer-app-1.0.0.jar)__                              | 

__[Full set of latest pre-built applications](https://github.com/raydac/langtrainer/releases/latest)__