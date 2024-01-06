FROM maven:3.8.3-openjdk-8

# Install nodejs for log processing
ENV NVM_DIR /usr/local/nvm
RUN mkdir -p $NVM_DIR
RUN curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.33.1/install.sh | bash
ENV NODE_VERSION v20
RUN /bin/bash -c "source $NVM_DIR/nvm.sh && nvm install $NODE_VERSION && nvm use --delete-prefix $NODE_VERSION"

WORKDIR /usr/src/parser
ADD . /usr/src/parser
RUN mvn -q -f /usr/src/parser/pom.xml clean install -U

CMD ["java", "-jar", "-Xmx1024m", "/usr/src/parser/target/stats-0.1.0.jar", "5600"]
