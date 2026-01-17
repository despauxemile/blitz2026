#!/usr/bin/env bash

set -e

rm -f remise.zip

zip -r remise.zip . \
    -x "./remise.zip" \
    -x "./target" -x "./target/**" \
    -x "./.git" -x "./.git/**"
