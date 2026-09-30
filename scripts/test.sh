#!/bin/bash

curl localhost:5600 --data-binary "@/home/howardc/parser/8583844960_1679394786.dem"

curl localhost:5600/blob?replay_url=http://replay117.valve.net/570/9014826734_1884764790.dem.bz2
curl localhost:5600/blob?replay_url=http://www.example.com
curl localhost:5600/blob?replay_url=https://odota.github.io/testfiles/1781962623_1.dem
curl localhost:5600/blob?replay_url=http://replay204.valve.net/570/1203069498_2076255461.dem.bz2