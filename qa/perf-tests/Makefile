
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

targetFile := ${shell ls target/tngp-qa-perf-tests-*-jar-with-dependencies.jar}

deploy-broker: undeploy-broker
	scripts/deploy-broker.sh ${WORKDIR} ${BROKER_HOST} ${BROKER_USERNAME}

undeploy-broker:
	-scripts/undeploy-broker.sh ${WORKDIR} ${BROKER_HOST} ${BROKER_USERNAME}
save-log:
	-scripts/downloadLog.sh ${WORKDIR} ${BROKER_HOST} ${BROKER_USERNAME}

local: $(eval BROKER_HOST = localhost)
 
test-start-wf-instance-latency:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
		 -Dtest.outputFileName=data/test-start-wf-instance-latency.txt \
		 -cp ${targetFile} \
		 org.camunda.tngp.perftest.StartWorkflowInstanceLatencyTest
	gnuplot etc/test-start-wf-instance-latency.gpi

test-start-wf-instance-throughput:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
		 -Dtest.outputFileName=data/test-start-wf-instance-throughput.txt \
		 -cp ${targetFile} \
		 org.camunda.tngp.perftest.StartWorkflowInstanceThroughputTest
	gnuplot \
         -e "outfile='data/test-start-wf-instance-throughput.png'; infile='data/test-start-wf-instance-throughput.txt'; plot_title='Start WF Instance Throughput'; plot_ylabel='Requests Sent'" \
         etc/throughput-graph.gpi

test-create-task-latency:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
         -Dtest.outputFileName=data/test-create-task-latency.txt \
         -cp ${targetFile} \
         org.camunda.tngp.perftest.CreateTaskLatencyTest
	gnuplot etc/test-create-task-latency.gpi

test-create-task-throughput:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
         -Dtest.outputFileName=data/test-create-task-throughput.txt \
         -cp ${targetFile} \
         org.camunda.tngp.perftest.CreateTaskThroughputTest
	gnuplot \
         -e "outfile='data/test-create-task-throughput.png'; infile='data/test-create-task-throughput.txt'; plot_title='Create Task Throughput'; plot_ylabel='Requests Sent'" \
         etc/throughput-graph.gpi

test-create-task-throughput-idle-subscription:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
         -Dtest.outputFileName=data/test-create-task-throughput-idle-subscription.txt \
         -cp ${targetFile} \
         org.camunda.tngp.perftest.CreateTaskThroughputIdleSubscriptionTest
	gnuplot \
         -e "outfile='data/test-create-task-throughput-idle-subscription.png'; infile='data/test-create-task-throughput-idle-subscription.txt'; plot_title='Create Task Throughput'; plot_ylabel='Requests Sent'" \
         etc/throughput-graph.gpi

test-task-subscription-throughput:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
         -Dtest.outputFileName=data/test-task-subscription-throughput.txt \
         -cp ${targetFile} \
         org.camunda.tngp.perftest.TaskSubscriptionThroughputTest
	gnuplot \
         -e "outfile='data/test-task-subscription-throughput.png'; infile='data/test-task-subscription-throughput.txt'; plot_title='Task Subscription Throughput'; plot_ylabel='Tasks Handled'" \
         etc/throughput-graph.gpi

test-topic-subscription-throughput:
	$(JAVA) -Dtngp.client.broker.contactPoint=$(BROKER_HOST):51015 \
         -Dtest.outputFileName=data/test-topic-subscription-throughput.txt \
         -cp ${targetFile} \
         org.camunda.tngp.perftest.TopicSubscriptionThroughputTest
	gnuplot \
         -e "outfile='data/test-topic-subscription-throughput.png'; infile='data/test-topic-subscription-throughput.txt'; plot_title='Topic Subscription Throughput'; plot_ylabel='Events Received'" \
         etc/throughput-graph.gpi

clean: undeploy-broker
	scripts/clean.sh ${WORKDIR} ${BROKER_HOST} ${BROKER_USERNAME}

build:
	cd ../.. && mvn clean package -U -DskipTests

all-tests: test-create-task-throughput test-create-task-latency test-start-wf-instance-latency test-start-wf-instance-throughput

jenkins: build deploy-broker all-tests save-log clean

.PHONY: save-log local deploy-broker undeploy-broker clean build test-start-wf-instance-latency test-create-task-throughput test-start-wf-instance-throughput all-tests jenkins
