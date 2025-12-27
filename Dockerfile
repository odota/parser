FROM maven:3-openjdk-18-slim

RUN apt-get update

# Install bzip2 for decompression
RUN apt-get install bzip2

# Install nodejs for log processing
# ARG NODE_VERSION=20.10.0
# ARG TARGETPLATFORM
# RUN if [ "$TARGETPLATFORM" = "linux/arm64" ]; then ARCH="arm64"; else ARCH="x64"; fi \
#  && curl https://nodejs.org/dist/v$NODE_VERSION/node-v$NODE_VERSION-linux-$ARCH.tar.gz | tar -xz -C /usr/local --strip-components 1

WORKDIR /usr/src/parser
ADD . /usr/src/parser
RUN mvn -q -f /usr/src/parser/pom.xml clean install -U

CMD ["java", "-jar", "/usr/src/parser/target/stats-0.1.0.jar", "5600"]
