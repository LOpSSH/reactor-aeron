#!/usr/bin/env bash

cd $(dirname $0)
cd ../../../

JAR_FILE=$(ls target |grep jar)

java \
    -cp target/${JAR_FILE}:target/lib/* \
    -XX:BiasedLockingStartupDelay=0 \
    -Djava.net.preferIPv4Stack=true \
    -Daeron.term.buffer.sparse.file=false \
    -Daeron.threading.mode=SHARED \
    -Dagrona.disable.bounds.checks=true \
    -Dreactor.aeron.sample.idle.strategy=yielding \
    -Dreactor.aeron.sample.frameCountLimit=16384 \
    -Dreactor.aeron.sample.messageLength=2048 \
    -Daeron.mtu.length=16k \
    -Daeron.socket.so_sndbuf=2m \
    -Daeron.socket.so_rcvbuf=2m \
    -Daeron.rcv.initial.window.length=2m \
    ${JVM_OPTS} reactor.aeron.rsocket.aeron.RSocketAeronServerTps
