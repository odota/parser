#!/bin/bash

# To build on M1 MacOS specify the platform argument
# For example: sh scripts/rebuild.sh --platform linux/arm64/v8
sudo docker build -t odota/parser $1 .
sudo docker rm -fv parser
sudo docker run -d --name parser -p 5600:5600 odota/parser