#!/bin/bash
# Running this script will install all dependencies needed for all of the projects 

# ensure that we have the correct version of all submodules
git submodule init
git submodule sync
git submodule update

# for react-native
npm install react-native@0.27.2 react@15.1.0 --silent
