FROM openjdk:8

# Maven
ENV MAVEN_HOME="/usr/share/maven"
ENV MAVEN_VERSION="3.3.9"
RUN cd / && \
    wget -q "http://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz" -O - | tar xvzf - && \
    mv /apache-maven-$MAVEN_VERSION /usr/share/maven && \
    ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

WORKDIR /usr/src/parser
ADD . /usr/src/parser
RUN mvn -q -f /usr/src/parser/pom.xml clean install -U

CMD ["java", "-jar", "-Xmx256m", "/usr/src/parser/target/stats-0.1.0.jar", "5600"]