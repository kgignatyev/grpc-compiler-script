gRPC compile script generator
---

this plugin generates shell script that can be used to generate Java and NPM libraries for grpc services


Development
---

to check generated resulsts run

    mvn clean install; mvn -f test-pom.xml grpc-compiler-script:generate-build-script
    
to debug plugin

    mvn clean install; mvnDebug -f test-pom.xml grpc-compiler-script:generate-build-script

try interactively in Docker 

    ./test-via-docker-image.sh
    # in the docker shell 
    mvn clean install; mvn -f test-pom.xml grpc-compiler-script:generate-build-script
    ./compile-grpc.sh

Use
---
