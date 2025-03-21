/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public final class MultiInstanceLargeInputCollectionTest {
  private static final int INPUT_COLLECTION_SIZE = 100;
  private static final String INPUT_ELEMENT = "inputElement";
  private static final int MAX_MESSAGE_SIZE_KB = 16;
  private static final ZeebeObjectMapper OBJECT_MAPPER = new ZeebeObjectMapper();
  private static final EmbeddedBrokerRule BROKER_RULE =
      new EmbeddedBrokerRule(
          cfg -> {
            cfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(MAX_MESSAGE_SIZE_KB));
            cfg.getProcessing().setMaxCommandsInBatch(1);
          });
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCompleteParallelMultiInstanceWithLargeInputCollection() {
    // given
    final long processKey =
        CLIENT_RULE.deployProcess(
            Bpmn.createExecutableProcess("PROCESS")
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("task")
                            .multiInstance(
                                mi ->
                                    mi.parallel()
                                        .zeebeInputCollectionExpression(
                                            createInputCollection(INPUT_COLLECTION_SIZE))
                                        .zeebeInputElement(INPUT_ELEMENT)))
                .endEvent()
                .done());

    // when
    final var processInstanceKey = CLIENT_RULE.createProcessInstance(processKey);
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("task")
        .limit(INPUT_COLLECTION_SIZE)
        .collect(Collectors.toList());
    final var actualInputElements = completeJobs();

    // then
    hasCreatedJobForEachInputElement(actualInputElements);
    hasCompletedElementsAndProcessInCorrectSequence(processInstanceKey);
    hasActivatedElementInBatches(processInstanceKey);
  }

  private String createInputCollection(final int size) {
    final var inputCollection = new ArrayList<Integer>();
    for (int i = 0; i < size; i++) {
      inputCollection.add(i);
    }
    return OBJECT_MAPPER.toJson(inputCollection);
  }

  private Set<Integer> completeJobs() {
    final ActivateJobsResponse activatedJobs =
        CLIENT_RULE
            .getClient()
            .newActivateJobsCommand()
            .jobType("task")
            .maxJobsToActivate(INPUT_COLLECTION_SIZE)
            .fetchVariables(INPUT_ELEMENT)
            .send()
            .join();

    final var inputElements = new HashSet<Integer>();
    activatedJobs
        .getJobs()
        .forEach(
            job -> {
              inputElements.add((Integer) job.getVariablesAsMap().get(INPUT_ELEMENT));
              CLIENT_RULE.getClient().newCompleteCommand(job.getKey()).send().join();
            });
    return inputElements;
  }

  private void hasCreatedJobForEachInputElement(final Set<Integer> actualInputElements) {
    final var expectedInputElements = new HashSet<Integer>();
    for (int i = 0; i < INPUT_COLLECTION_SIZE; i++) {
      expectedInputElements.add(i);
    }
    assertThat(actualInputElements).containsAll(expectedInputElements);
  }

  private void hasCompletedElementsAndProcessInCorrectSequence(final long processInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .map(record -> record.getValue().getBpmnElementType())
        .describedAs("Has completed process and all jobs")
        // Plus 4 for the start event, multi instance, end event and process
        .hasSize(INPUT_COLLECTION_SIZE + 4)
        .describedAs("Completed in correct sequence")
        .startsWith(BpmnElementType.START_EVENT, BpmnElementType.SERVICE_TASK)
        .endsWith(
            BpmnElementType.SERVICE_TASK,
            BpmnElementType.MULTI_INSTANCE_BODY,
            BpmnElementType.END_EVENT,
            BpmnElementType.PROCESS);
  }

  private void hasActivatedElementInBatches(final long processInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceBatchRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(p -> p.getIntent() == ProcessInstanceBatchIntent.ACTIVATED))
        .describedAs(
            "Has activated in multiple batches. If this assertion fails please decrease "
                + "the message size, or increase the input collection.")
        .hasSize(INPUT_COLLECTION_SIZE + 1)
        .extracting(r -> r.getIntent())
        .endsWith(ProcessInstanceBatchIntent.ACTIVATED);
  }
}
