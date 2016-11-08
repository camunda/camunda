
# preconditions:
#
# * compatible java version is in path on all nodes
# * maven is in path on client node
# * it is possible to ssh to the broker node without passphrase
# TODO: assert this in a script

BROKER_HOST = 192.168.0.11
BROKER_USERNAME = camunda
WORKDIR = work/
ifneq ($(JAVA_HOME),)
  JAVA = "$(JAVA_HOME)"/bin/java
else
  JAVA = java
endif

deploy-broker: undeploy-broker
	scripts/deploy-broker.sh ${WORKDIR} ${BROKER_HOST} ${BROKER_USERNAME}

undeploy-broker:
	-scripts/undeploy-broker.sh ${WORKDIR} ${BROKER_HOST} ${BROKER_USERNAME}

test-start-wf-instance-latency:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
		 -Dtest.outputFileName=data/test-start-wf-instance-latency.txt \
		 -cp target/tngp-qa-perf-tests-0.1.0-SNAPSHOT-jar-with-dependencies.jar \
		 org.camunda.tngp.perftest.StartWorkflowInstanceLatencyTest
	gnuplot etc/test-start-wf-instance-latency.gpi

test-start-wf-instance-throughput:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
		 -Dtest.outputFileName=data/test-start-wf-instance-throughput.txt \
		 -cp target/tngp-qa-perf-tests-0.1.0-SNAPSHOT-jar-with-dependencies.jar \
		 org.camunda.tngp.perftest.StartWorkflowInstanceThroughputTest
	gnuplot etc/test-start-workflow-instance-throughput.gpi

test-create-task-latency:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
         -Dtest.outputFileName=data/test-create-task-latency.txt \
         -cp target/tngp-qa-perf-tests-0.1.0-SNAPSHOT-jar-with-dependencies.jar \
         org.camunda.tngp.perftest.CreateTaskLatencyTest
	gnuplot etc/test-create-task-latency.gpi

test-create-task-throughput:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
         -Dtest.outputFileName=data/test-create-task-throughput.txt \
         -cp target/tngp-qa-perf-tests-0.1.0-SNAPSHOT-jar-with-dependencies.jar \
         org.camunda.tngp.perftest.CreateTaskThroughputTest
	gnuplot etc/test-create-task-throughput.gpi

test-create-task-throughput-idle-subscription:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
         -Dtest.outputFileName=data/test-create-task-throughput-idle-subscription.txt \
         -cp target/tngp-qa-perf-tests-0.1.0-SNAPSHOT-jar-with-dependencies.jar \
         org.camunda.tngp.perftest.CreateTaskThroughputIdleSubscriptionTest
	gnuplot etc/test-create-task-throughput-idle-subscription.gpi

test-task-subscription-throughput:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
         -Dtest.outputFileName=data/test-task-subscription-throughput.txt \
         -cp target/tngp-qa-perf-tests-0.1.0-SNAPSHOT-jar-with-dependencies.jar \
         org.camunda.tngp.perftest.TaskSubscriptionThroughputTest
	gnuplot etc/test-task-subscription-throughput.gpi

clean: undeploy-broker
	scripts/clean.sh ${WORKDIR} ${BROKER_HOST} ${BROKER_USERNAME}

build:
	cd ../.. && mvn clean package -U -DskipTests
	mvn clean package -U

all-tests: test-create-task-throughput test-create-task-latency test-start-wf-instance-latency test-start-wf-instance-throughput

jenkins: build deploy-broker all-tests clean

.PHONY: deploy-broker undeploy-broker clean build test-start-wf-instance-latency test-create-task-throughput test-start-wf-instance-throughput all-tests jenkins
