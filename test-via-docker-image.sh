#!/usr/bin/env bash

docker run \
  -v $(pwd):/root \
  -v $HOME/.m2:/root/.m2 \
  -ti --rm kgignatyev/grpc-compiler:v1 bash
