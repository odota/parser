#!/bin/bash

curl localhost:5600 --data-binary "@/home/howardc/parser/8583844960_1679394786.dem"
curl "localhost:5600?blob" --data-binary "@/home/howardc/parser/8583844960_1679394786.dem"

curl localhost:5600/blob?replay_url=http://replay271.valve.net/570/8623065989_1426557301.dem.bz2