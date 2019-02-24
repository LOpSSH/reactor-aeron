#!/usr/bin/env bash

cd $(dirname $0)
cd ../../../

JAR_FILE=$(ls target |grep jar)

java \
    -cp target/${JAR_FILE}:target/lib/* \
    -XX:+UnlockDiagnosticVMOptions \
    -XX:GuaranteedSafepointInterval=300000 \
    ${JVM_OPTS} reactor.aeron.rsocket.netty.RSocketNettyClientTps
