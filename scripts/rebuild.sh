#!/bin/bash

sudo docker build -t yasp/parser .
sudo docker rm -fv parser
sudo docker run -d --name parser --net=host yasp/parser