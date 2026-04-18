#!/bin/bash

# Script just generates free desktop descriptor to start application

LANGTRAINER_HOME="$(realpath $(dirname ${BASH_SOURCE[0]}))"
TARGET=$LANGTRAINER_HOME/langtrainer.desktop

echo [Desktop Entry] > $TARGET
echo Encoding=UTF-8 >> $TARGET
echo Name=LangTrainer >> $TARGET
echo Comment=LangTrainer application >> $TARGET
echo GenericName=LangTrainer >> $TARGET
echo Exec=$LANGTRAINER_HOME/run.sh >> $TARGET
echo Terminal=false >> $TARGET
echo Type=Application >> $TARGET
echo Icon=$LANGTRAINER_HOME/icon.svg >> $TARGET
echo "Categories=Application;" >> $TARGET
echo "Keywords=langtrainer;dialogs;words;" >> $TARGET
echo StartupWMClass=LangTrainerApp >> $TARGET
echo StartupNotify=true >> $TARGET

echo Desktop script has been generated: $TARGET

if [ -d ~/.gnome/apps ]; then
    echo copy to ~/.gnome/apps
    cp -f $TARGET ~/.gnome/apps
fi

if [ -d ~/.local/share/applications ]; then
    echo copy to ~/.local/share/applications
    cp -f $TARGET ~/.local/share/applications
fi

if [ -d ~/Desktop ]; then
    echo copy to ~/Desktop
    cp -f $TARGET ~/Desktop
fi

