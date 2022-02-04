/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.camunda.zeebe.dispatcher.impl.log.LogBufferAppender;
import io.camunda.zeebe.logstreams.impl.log.LogEntryDescriptor;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.broker.protocol.commandapi.CommandApiRule;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.netty.util.NetUtil;
import java.util.Map;
import org.agrona.BitUtil;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public final class JobBatchTruncationIT {
  private final EmbeddedBrokerRule broker =
      new EmbeddedBrokerRule(cfg -> cfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(32)));
  private final CommandApiRule command = new CommandApiRule(broker::getAtomixCluster);

  @Rule public RuleChain chain = RuleChain.outerRule(broker).around(command);

  private ZeebeClient client;

  @Before
  public void before() {
    final var gatewayAddress = NetUtil.toSocketAddressString(broker.getGatewayAddress());
    client = ZeebeClient.newClientBuilder().gatewayAddress(gatewayAddress).usePlaintext().build();
  }

  @After
  public void after() {
    CloseHelper.closeAll(client);
  }

  @Test
  public void shouldTruncateJobBatchesToAvoidDispatcherThreshold() {
    // given
    final var maxMessageSize = getMaxDispatcherFragmentLength();
    final var processId = Strings.newRandomValidBpmnId();
    final var taskType = Strings.newRandomValidBpmnId();
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType(taskType))
            .endEvent()
            .done();
    final var processDefinitionKey =
        command.partitionClient().deployProcess(process).getProcessDefinitionKey();
    final var jobRecordLength =
        new JobRecord()
            .setRetries(1)
            .setType(taskType)
            .setDeadline(1L)
            .setRetryBackoff(1L)
            .setElementId("task")
            .setBpmnProcessId(processId)
            .setProcessInstanceKey(1L)
            .getLength();
    final var limitVariableLength =
        maxMessageSize
            - jobRecordLength
            - new JobBatchRecord().setType(taskType).getLength()
            - new RecordMetadata().getLength()
            - LogEntryDescriptor.HEADER_BLOCK_LENGTH
            - getDispatcherFrameLength()
            - 312;

    // when
    // start 3 processes with large variables; we split them to ensure we don't run into the
    // dispatcher's batch limit when creating the process instances
    final var firstProcess =
        command
            .partitionClient()
            .createProcessInstance(r -> r.setProcessDefinitionKey(processDefinitionKey));
    final var secondProcess =
        command
            .partitionClient()
            .createProcessInstance(r -> r.setProcessDefinitionKey(processDefinitionKey));

    command
        .partitionClient()
        .updateVariables(
            firstProcess.getProcessInstanceKey(),
            Map.of("foo", "x".repeat(limitVariableLength / 2)));
    command
        .partitionClient()
        .updateVariables(
            secondProcess.getProcessInstanceKey(),
            Map.of("foo", "x".repeat(limitVariableLength / 2)));

    // wait for all the jobs to be ready as otherwise we won't be able to activate them
    Awaitility.await("until all jobs are created and can be activated")
        .untilAsserted(
            () -> {
              final var jobCount = RecordingExporter.jobRecords(JobIntent.CREATED).limit(2).count();
              assertThat(jobCount).as("there are 3 jobs that can be activated").isEqualTo(2);
            });

    // activate the jobs in two batches (since we should be able to fit at least two), with a high
    // timeout to avoid race conditions and the second batch picking up the timed out jobs from the
    // first batch
    final var firstBatch =
        command
            .partitionClient()
            .activateJobBatch(
                b -> b.setMaxJobsToActivate(10).setTimeout(30 * 1000).setType(taskType));
    final var secondBatch =
        command
            .partitionClient()
            .activateJobBatch(
                b -> b.setMaxJobsToActivate(10).setTimeout(30 * 1000).setType(taskType));

    // then
    assertThat(firstBatch.getJobs()).hasSize(1);
    assertThat(firstBatch.getTruncated()).isTrue();
    assertThat(secondBatch.getJobs()).hasSize(1);
    assertThat(secondBatch.getTruncated()).isFalse();
  }

  private int getMaxDispatcherFragmentLength() {
    final var maxDispatcherFragmentSize =
        (int) broker.getBrokerCfg().getNetwork().getMaxMessageSizeInBytes();
    final var dispatcherFrameLength =
        BitUtil.align(
            LogBufferAppender.claimedBatchLength(1, maxDispatcherFragmentSize)
                - maxDispatcherFragmentSize,
            DataFrameDescriptor.FRAME_ALIGNMENT);
    return maxDispatcherFragmentSize - dispatcherFrameLength;
  }

  private int getDispatcherFrameLength() {
    final var maxDispatcherFragmentSize =
        (int) broker.getBrokerCfg().getNetwork().getMaxMessageSizeInBytes();
    return BitUtil.align(
        LogBufferAppender.claimedBatchLength(1, maxDispatcherFragmentSize)
            - maxDispatcherFragmentSize,
        DataFrameDescriptor.FRAME_ALIGNMENT);
  }
}
