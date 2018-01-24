
filter?=''

build: target/
	mvn clean install dependency:copy-dependencies -DskipTests

run: build
	java -cp target/dependency/*:target/zeebe-broker-core-0.7.0-SNAPSHOT.jar:/usr/lib/jvm/default/lib/tools.jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1044 io.zeebe.broker.StandaloneBroker
#	-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=1044 
#
profile: build 
	java -javaagent:zb-profiler-1.0-SNAPSHOT-jar-with-dependencies.jar=$(filter) -cp target/dependency/*:target/zeebe-broker-core-0.7.0-SNAPSHOT.jar io.zeebe.broker.StandaloneBroker

honest-profile: build
	java  -agentpath:/home/zell/profiler/liblagent.so=interval=7,logPath=/tmp/log.hpl -cp target/dependency/*:target/zeebe-broker-core-0.7.0-SNAPSHOT.jar io.zeebe.broker.StandaloneBroker

all: build run

