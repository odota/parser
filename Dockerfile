FROM maven:3.8.3-openjdk-8

WORKDIR /usr/src/parser
ADD . /usr/src/parser
RUN mvn -q -f /usr/src/parser/pom.xml clean install -U

CMD ["java", "-jar", "-Xmx512m", "/usr/src/parser/target/stats-0.1.0.jar", "5600"]
