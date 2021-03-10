/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.client.command;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertIncidentResolved;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCreated;
import static io.zeebe.protocol.record.intent.IncidentIntent.CREATED;

import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.test.util.BrokerClassRuleHelper;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class IncidentTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private long processDefinitionKey;
  private String jobType;

  @Before
  public void setUp() {

    jobType = helper.getJobType();

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "failingTask", t -> t.zeebeJobType(jobType).zeebeInputExpression("foo", "foo"))
            .done();

    processDefinitionKey = CLIENT_RULE.deployProcess(process);
  }

  @Test
  public void shouldRejectResolveOnNonExistingIncident() {
    // given
    final int partition = CLIENT_RULE.getPartitions().get(0);
    final long nonExistingKey = Protocol.encodePartitionId(partition, 123);

    // when
    final var expectedMessage =
        String.format(
            "Expected to resolve incident with key '%d', but no such incident was found",
            nonExistingKey);

    Assertions.assertThatThrownBy(
            () -> CLIENT_RULE.getClient().newResolveIncidentCommand(nonExistingKey).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(expectedMessage);
  }

  @Test
  public void shouldResolveIncident() {
    // given
    final long processInstanceKey = CLIENT_RULE.createProcessInstance(processDefinitionKey);

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(CREATED).getFirst();

    // when
    CLIENT_RULE
        .getClient()
        .newSetVariablesCommand(processInstanceKey)
        .variables(Map.of("foo", "bar"))
        .send()
        .join();

    CLIENT_RULE.getClient().newResolveIncidentCommand(incident.getKey()).send().join();

    // then
    assertJobCreated(jobType);
    assertIncidentResolved();
  }

  @Test
  public void shouldRejectDuplicateResolving() {
    // given
    final long processInstanceKey = CLIENT_RULE.createProcessInstance(processDefinitionKey);

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(CREATED).getFirst();

    // when
    CLIENT_RULE
        .getClient()
        .newSetVariablesCommand(processInstanceKey)
        .variables(Map.of("foo", "bar"))
        .send()
        .join();

    CLIENT_RULE.getClient().newResolveIncidentCommand(incident.getKey()).send().join();

    // then
    assertJobCreated(jobType);
    assertIncidentResolved();

    final var expectedMessage =
        String.format(
            "Expected to resolve incident with key '%d', but no such incident was found",
            incident.getKey());

    Assertions.assertThatThrownBy(
            () ->
                CLIENT_RULE.getClient().newResolveIncidentCommand(incident.getKey()).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(expectedMessage);
  }
}
