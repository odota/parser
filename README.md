# parser
Replay parse server generating logs from replay files

Quickstart
----
* Run the Java project (it'll start a webserver on port 5600)
* POST a .dem replay file to the server (example in scripts/test.sh)
* The parser returns line-delimited JSON in the HTTP response