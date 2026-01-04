#!/bin/bash

curl localhost:5600 --data-binary "@/home/howardc/parser/8583844960_1679394786.dem"

curl localhost:5600/blob?replay_url=http://replay271.valve.net/570/8623065989_1426557301.dem.bz2
curl localhost:5600/blob?replay_url=http://www.example.com
curl localhost:5600/blob?replay_url=https://odota.github.io/testfiles/1781962623_1.dem
curl localhost:5600/blob?replay_url=http://replay113.valve.net/570/2562582896_1605238528.dem.bz2