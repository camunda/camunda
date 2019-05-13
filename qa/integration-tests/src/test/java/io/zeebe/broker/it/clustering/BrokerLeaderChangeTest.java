/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.clustering;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCompleted;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCompleted;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStreamServiceName;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.core.election.LeaderElection;
import io.zeebe.broker.Broker;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.partitions.PartitionLeaderElection;
import io.zeebe.broker.clustering.base.partitions.PartitionServiceNames;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.util.RecordStream;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.PartitionInfo;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.logstreams.impl.service.LogStreamServiceNames;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.ServiceName;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BrokerLeaderChangeTest {
  public static final String NULL_VARIABLES = null;
  public static final String JOB_TYPE = "testTask";
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  public Timeout testTimeout = Timeout.seconds(120);
  public ClusteringRule clusteringRule = new ClusteringRule(1, 3, 3);
  public GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Test
  public void shouldBecomeFollowerAfterRestartLeaderChange() {
    // given
    final int partition = Protocol.START_PARTITION_ID;
    final int oldLeader = clusteringRule.getLeaderForPartition(partition).getNodeId();

    clusteringRule.stopBroker(oldLeader);

    waitUntil(() -> clusteringRule.getLeaderForPartition(partition).getNodeId() != oldLeader);

    // when
    clusteringRule.restartBroker(oldLeader);

    // then

    final Stream<PartitionInfo> partitionInfo =
        clusteringRule.getTopologyFromClient().getBrokers().stream()
            .filter(b -> b.getNodeId() == oldLeader)
            .flatMap(b -> b.getPartitions().stream().filter(p -> p.getPartitionId() == partition));

    assertThat(partitionInfo.allMatch(p -> !p.isLeader())).isTrue();
  }

  @Test
  public void shouldReactToLeaderChanges() {
    // given
    final int partition = Protocol.START_PARTITION_ID;
    final String partitionName = Partition.getPartitionName(partition);
    final int oldLeaderId = clusteringRule.getLeaderForPartition(partition).getNodeId();
    final Broker oldLeader = getBroker(oldLeaderId);
    assertBrokerHasService(
        oldLeader, PartitionServiceNames.leaderPartitionServiceName(partitionName));

    // get leader election primitive for a partition
    final LeaderElection<String> election = getElection(oldLeader, partition).getElection();
    assertThat(election.getLeadership().leader().id()).isEqualTo(String.valueOf(oldLeaderId));

    // when

    // Force another node to become leader
    election.evict(String.valueOf(oldLeaderId));
    assertThat(election.getLeadership().leader().id()).isNotEqualTo(String.valueOf(oldLeaderId));

    waitUntil(() -> clusteringRule.getLeaderForPartition(partition).getNodeId() != oldLeaderId);

    // then

    // leader services are started.
    final int newLeaderId = clusteringRule.getLeaderForPartition(partition).getNodeId();
    final Broker newLeader = getBroker(newLeaderId);

    assertBrokerHasService(
        newLeader, LogStreamServiceNames.logStorageAppenderServiceName(partitionName));
    assertBrokerHasService(
        newLeader, PartitionServiceNames.leaderPartitionServiceName(partitionName));
    assertBrokerNoService(
        newLeader, PartitionServiceNames.followerPartitionServiceName(partitionName));

    // oldLeader has stopped leader services
    assertBrokerNoService(
        oldLeader, LogStreamServiceNames.logStorageAppenderServiceName(partitionName));
    assertBrokerHasService(
        oldLeader, PartitionServiceNames.followerPartitionServiceName(partitionName));
  }

  @Test
  public void shouldReactToFastLeaderChanges() {
    // given
    final int partition = Protocol.START_PARTITION_ID;
    final String partitionName = Partition.getPartitionName(partition);

    // get leader election primitive for a partition
    final LeaderElection<String> election = getElection(getBroker(0), partition).getElection();

    // when

    // Force repeated leader change
    election.async().anoint("0");
    election.async().anoint("1");
    election.anoint("2");

    assertThat(election.getLeadership().leader().id()).isEqualTo(String.valueOf(2));
    waitUntil(() -> clusteringRule.getLeaderForPartition(partition).getNodeId() == 2);

    // then

    // leader services are started.
    final Broker newLeader = getBroker(2);

    assertBrokerHasService(
        newLeader, LogStreamServiceNames.logStorageAppenderServiceName(partitionName));
    assertBrokerHasService(
        newLeader, PartitionServiceNames.leaderPartitionServiceName(partitionName));
    assertBrokerNoService(
        newLeader, PartitionServiceNames.followerPartitionServiceName(partitionName));

    // other nodes has stopped leader services
    assertBrokerNoService(
        getBroker(0), PartitionServiceNames.leaderPartitionServiceName(partitionName));
    assertBrokerHasService(
        getBroker(0), PartitionServiceNames.followerPartitionServiceName(partitionName));

    assertBrokerNoService(
        getBroker(1), PartitionServiceNames.leaderPartitionServiceName(partitionName));
    assertBrokerHasService(
        getBroker(1), PartitionServiceNames.followerPartitionServiceName(partitionName));
  }

  @Test
  public void shouldContinueProcessingAfterLeaderChange() {
    // given
    final int partition = 1;
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(partition);

    final long jobKey = clientRule.createSingleJob(JOB_TYPE);

    // when
    final LeaderElection<String> election =
        getElection(getBroker(leaderForPartition.getNodeId()), partition).getElection();
    // Force another node to become leader
    election.evict(String.valueOf(leaderForPartition.getNodeId()));
    waitUntil(
        () ->
            clusteringRule.getLeaderForPartition(partition).getNodeId()
                != leaderForPartition.getNodeId());

    final JobCompleter jobCompleter = new JobCompleter(jobKey);

    // then
    jobCompleter.waitForJobCompletion();

    jobCompleter.close();
  }

  @Test
  public void shouldNotReProcessEventsWhenTwoStreamProcessorsDuringLeaderChange() {
    // given
    final int partition = 1;
    final String partitionName = Partition.getPartitionName(partition);
    final int oldLeaderId = clusteringRule.getLeaderForPartition(partition).getNodeId();
    final Broker oldLeader = getBroker(oldLeaderId);

    // Deploy some workflow
    final long workflowKey = clientRule.deployWorkflow(WORKFLOW);

    assertBrokerHasService(
        oldLeader, PartitionServiceNames.leaderPartitionServiceName(partitionName));

    // get leaderelection primitive for a partition
    final PartitionLeaderElection partitionLeaderElection = getElection(oldLeader, partition);
    final LeaderElection<String> election = partitionLeaderElection.getElection();
    assertThat(election.getLeadership().leader().id()).isEqualTo(String.valueOf(oldLeaderId));

    // Remove leader election listener. So when new leader starts, old leader is not
    // listening to the leadership events.
    election.removeListener(partitionLeaderElection);

    final long workflowInstanceKey1 = clientRule.createWorkflowInstance(workflowKey);
    final long workflowInstanceKey2 = clientRule.createWorkflowInstance(workflowKey);

    // when
    // force another node to become leader
    election.evict(String.valueOf(oldLeaderId));
    assertThat(election.getLeadership().leader().id()).isNotEqualTo(String.valueOf(oldLeaderId));

    // Check if leader services are started.
    final int newLeaderId = clusteringRule.getLeaderForPartition(partition).getNodeId();
    final Broker newLeader = getBroker(newLeaderId);
    assertBrokerHasService(
        newLeader, PartitionServiceNames.leaderPartitionServiceName(partitionName));

    // At this point there are two streamprocessors
    // oldleader still has leader services because it is not listening to leadership events.
    assertBrokerHasService(
        oldLeader, PartitionServiceNames.leaderPartitionServiceName(partitionName));

    assertWorkflowInstanceCompleted("process", workflowInstanceKey1);
    assertWorkflowInstanceCompleted("process", workflowInstanceKey2);

    oldLeader
        .getBrokerContext()
        .getServiceContainer()
        .removeService(PartitionServiceNames.leaderPartitionServiceName(partitionName));
    oldLeader
        .getBrokerContext()
        .getServiceContainer()
        .removeService(PartitionServiceNames.leaderOpenLogStreamServiceName(partitionName));

    final List<TypedRecord<WorkflowInstanceRecord>> records =
        getRecordStream(newLeaderId, partition)
            .onlyWorkflowInstanceRecords()
            .collect(Collectors.toList());

    // then

    // If any of the records were processed by two stream processors, there will be more than 2
    // records of each type
    assertThat(
            records.stream()
                .filter(r -> r.getValue().getBpmnElementType() == BpmnElementType.PROCESS)
                .filter(
                    r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATING)
                .count())
        .isEqualTo(2); // Two workflows were created;

    assertThat(
            records.stream()
                .filter(r -> r.getValue().getBpmnElementType() == BpmnElementType.PROCESS)
                .filter(
                    r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .count())
        .isEqualTo(2); // Two workflows completed.
  }

  private PartitionLeaderElection getElection(Broker node, int partitionId) {
    final Injector<PartitionLeaderElection> leaderElectionInjector = new Injector<>();
    node.getBrokerContext()
        .getServiceContainer()
        .createService(ServiceName.newServiceName("test-leader-election", Void.class), () -> null)
        .dependency(
            PartitionServiceNames.partitionLeaderElectionServiceName(
                Partition.getPartitionName(partitionId)),
            leaderElectionInjector)
        .install()
        .join();
    return leaderElectionInjector.getValue();
  }

  private void assertBrokerHasService(Broker node, ServiceName service) {
    waitUntil(() -> node.getBrokerContext().getServiceContainer().hasService(service));
  }

  private void assertBrokerNoService(Broker node, ServiceName service) {
    waitUntil(() -> !node.getBrokerContext().getServiceContainer().hasService(service));
  }

  private Broker getBroker(int nodeId) {
    return clusteringRule.getBrokers().stream()
        .filter(b -> b.getConfig().getCluster().getNodeId() == nodeId)
        .findFirst()
        .get();
  }

  public RecordStream getRecordStream(int nodeId, int partitionId) {
    final Broker broker = getBroker(nodeId);
    final Injector<LogStream> logStreamInjector = new Injector<>();
    broker
        .getBrokerContext()
        .getServiceContainer()
        .createService(ServiceName.newServiceName("get-logstream", Void.class), () -> null)
        .dependency(
            logStreamServiceName(Partition.getPartitionName(partitionId)), logStreamInjector)
        .install()
        .join();

    final LogStream logStream = logStreamInjector.getValue();
    final LogStreamReader reader = new BufferedLogStreamReader(logStream);
    reader.seekToFirstEvent();
    final Iterable<LoggedEvent> iterable = () -> reader;
    return new RecordStream(StreamSupport.stream(iterable.spliterator(), false));
  }

  @Test
  public void shouldChangeLeaderAfterLeaderDies() {
    // given
    final BrokerInfo leaderForPartition = clusteringRule.getLeaderForPartition(1);

    final long jobKey = clientRule.createSingleJob(JOB_TYPE);

    // when
    clusteringRule.stopBroker(leaderForPartition.getNodeId());
    final JobCompleter jobCompleter = new JobCompleter(jobKey);

    // then
    jobCompleter.waitForJobCompletion();

    jobCompleter.close();
  }

  class JobCompleter {
    private final JobWorker jobWorker;
    private final CountDownLatch latch = new CountDownLatch(1);

    JobCompleter(final long jobKey) {

      jobWorker =
          clientRule
              .getClient()
              .newWorker()
              .jobType(JOB_TYPE)
              .handler(
                  (client, job) -> {
                    if (job.getKey() == jobKey) {
                      client.newCompleteCommand(job.getKey()).send();
                      latch.countDown();
                    }
                  })
              .open();
    }

    void waitForJobCompletion() {
      try {
        latch.await(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      assertJobCompleted();
    }

    void close() {
      if (!jobWorker.isClosed()) {
        jobWorker.close();
      }
    }
  }

  @Test
  public void shouldWithdrawAndRejoinElection() {
    // given
    final int partition = Protocol.START_PARTITION_ID;
    final String partitionName = Partition.getPartitionName(partition);
    final int oldLeaderId = clusteringRule.getLeaderForPartition(partition).getNodeId();
    final Broker oldLeader = getBroker(oldLeaderId);
    final PartitionLeaderElection election = getElection(oldLeader, partition);

    // when
    election.withdraw().join();
    assertThat(
            election
                .getElection()
                .getLeadership()
                .candidates()
                .contains(String.valueOf(oldLeaderId)))
        .isFalse();
    assertBrokerNoService(
        oldLeader, PartitionServiceNames.leaderPartitionServiceName(partitionName));

    election.join().join();

    // then
    assertThat(
            election
                .getElection()
                .getLeadership()
                .candidates()
                .contains(String.valueOf(oldLeaderId)))
        .isTrue();
  }
}
