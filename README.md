# parser
Replay parse server generating logs from replay files

Quickstart
----
* Run the Java project (it'll start a webserver on port 5600)
* Or build manually with `mvn install`
* Run manually with `java -jar target/stats-0.1.0.jar`
* POST a .dem replay file to the server (example: scripts/test.sh)
* The parser returns line-delimited JSON in the HTTP response