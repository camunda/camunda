/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.record.RecordStream;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import io.zeebe.containers.cluster.ZeebeCluster;
import io.zeebe.containers.engine.ContainerEngine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.StreamSupport;
import org.agrona.CloseHelper;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class JobPushIT {
  private final Network network = Network.newNetwork();
  private final ZeebeCluster cluster =
      ZeebeCluster.builder()
          .withBrokersCount(1)
          .withGatewaysCount(1)
          .withPartitionsCount(1)
          .withEmbeddedGateway(false)
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .build();

  @SuppressWarnings("unused")
  @RegisterExtension
  final ContainerLogsDumper logsWatcher = new ContainerLogsDumper(cluster::getNodes);

  @Container
  private final ContainerEngine engine =
      ContainerEngine.builder()
          .withDebugReceiverPort(SocketUtil.getNextAddress().getPort())
          .withCluster(cluster)
          .withIdlePeriod(Duration.ofSeconds(5))
          .build();

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(network);
  }

  @Test
  void shouldStreamActivatedJobs() throws InterruptedException, TimeoutException {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("foo"))
            .endEvent()
            .done();

    try (final var client = engine.createClient()) {
      final var deploymentEvent =
          client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

      // when
      final List<ActivatedJob> jobs = new ArrayList<>();
      client
          .newStreamJobsCommand()
          .jobType("foo")
          .consumer(jobs::add)
          .timeout(Duration.ofMinutes(2))
          .send();

      client
          .newCreateInstanceCommand()
          .processDefinitionKey(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey())
          .requestTimeout(Duration.ofMinutes(1))
          .send()
          .join();

      // then
      // wait for the record to be available and consumed
      engine.waitForIdleState(Duration.ofSeconds(30));
      assertThat(records())
          .as("job should become activated immediately")
          .extracting(Record::getValueType, Record::getIntent)
          .containsSubsequence(Tuple.tuple(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATED));
      assertThat(jobs).hasSize(1);
    }
  }

  @SuppressWarnings("unchecked")
  private RecordStream records() {
    return new RecordStream(
        StreamSupport.stream(BpmnAssert.getRecordStream().records().spliterator(), false)
            .map(r -> (Record<RecordValue>) r));
  }
}
