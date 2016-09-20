#!/bin/bash

sudo docker build -t odota/parser .
sudo docker rm -fv parser
sudo docker run -d --name parser --net=host odota/parser