FROM maven:3.8.3-openjdk-8

# Install nodejs for log processing
ARG NODE_VERSION=20.10.0
RUN /bin/bash -c 'set -ex && \
    if [ `uname -m` == "aarch64" ]; then \
       ARCH="arm64"
    else \
       ARCH="x64"
    fi'
RUN curl https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-$ARCH.tar.gz | tar -xz -C /usr/local --strip-components 1

WORKDIR /usr/src/parser
ADD . /usr/src/parser
RUN mvn -q -f /usr/src/parser/pom.xml clean install -U

CMD ["java", "-jar", "/usr/src/parser/target/stats-0.1.0.jar", "5600"]
