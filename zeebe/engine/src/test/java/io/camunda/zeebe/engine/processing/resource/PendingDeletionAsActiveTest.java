/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * A definition resting in {@code PENDING_DELETION} is a corruption artifact of the pre-8.9.18
 * PersistedProcess.wrap bug; the engine treats it as active. These tests drive the processor-level
 * gates that state-only tests cannot: redeploy duplicate detection, the delete gate, and
 * start-subscription handoff.
 */
public class PendingDeletionAsActiveTest {

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final SearchClientsProxy searchClientsProxy = Mockito.mock(SearchClientsProxy.class);

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition().withSearchClientsProxy(searchClientsProxy);

  @Before
  public void setUp() {
    when(searchClientsProxy.withSecurityContext(any())).thenReturn(searchClientsProxy);
    when(searchClientsProxy.searchProcessInstances(any(ProcessInstanceQuery.class)))
        .thenReturn(
            new SearchQueryResult.Builder<ProcessInstanceEntity>().items(List.of()).build());
  }

  @Test
  public void shouldTreatPendingDeletionLatestAsDuplicateOnIdenticalRedeploy() {
    // given - v1 is deployed and then stuck in PENDING_DELETION
    final var processId = helper.getBpmnProcessId();
    final var model = simpleProcess(processId);
    final var v1 = deploy(model);
    injectPendingDeletion(v1);

    // when - the identical resource is redeployed
    final var redeployed =
        engine
            .deployment()
            .withXmlResource(model)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();

    // then - it is a duplicate of the stuck latest, reusing its key and version rather than minting
    // a new version
    assertThat(redeployed.isDuplicate()).isTrue();
    assertThat(redeployed.getVersion()).isEqualTo(v1.getVersion());
    assertThat(redeployed.getProcessDefinitionKey()).isEqualTo(v1.getProcessDefinitionKey());
  }

  @Test
  public void shouldDeletePendingDeletionDefinitionThroughApi() {
    // given - v1 is deployed and then stuck in PENDING_DELETION, with no running instances
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(simpleProcess(processId));
    injectPendingDeletion(v1);

    // when - the stuck definition is deleted through the normal API
    engine.resourceDeletion().withResourceKey(v1.getProcessDefinitionKey()).delete();

    // then - the delete gate accepts it and the definition is finalized rather than rejected
    assertThat(
            RecordingExporter.processRecords()
                .withIntent(ProcessIntent.DELETED)
                .withProcessDefinitionKey(v1.getProcessDefinitionKey())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldResubscribeStartEventsToPendingDeletionVersionWhenLatestDeleted() {
    // given - v1 (message start) is stuck in PENDING_DELETION, v2 (message start) is the ACTIVE
    // latest that holds the start subscription
    final var processId = helper.getBpmnProcessId();
    final var v1 = deploy(messageStartProcess(processId, "end-v1"));
    final var v2 = deploy(messageStartProcess(processId, "end-v2"));
    injectPendingDeletion(v1);

    // when - the latest version is deleted (its start subscription must be handed down) and the
    // start message is published
    engine.resourceDeletion().withResourceKey(v2.getProcessDefinitionKey()).delete();
    engine.message().withName("message").withCorrelationKey("key").publish();

    // then - the handoff treated PENDING_DELETION v1 as active and resubscribed it, so the message
    // starts an instance of v1
    final var started =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(processId)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    assertThat(started.getValue().getProcessDefinitionKey())
        .isEqualTo(v1.getProcessDefinitionKey());
  }

  private static BpmnModelInstance simpleProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
  }

  private static BpmnModelInstance messageStartProcess(
      final String processId, final String endEventId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .message("message")
        .endEvent(endEventId)
        .done();
  }

  private ProcessMetadataValue deploy(final BpmnModelInstance model) {
    return engine
        .deployment()
        .withXmlResource(model)
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  /**
   * Seeds a resting {@code PENDING_DELETION} by writing a standalone {@code DELETING} event: its
   * applier sets the state without the {@code DELETED} that normally follows atomically,
   * reproducing the corrupted rest state that a real deletion cannot leave behind.
   */
  private void injectPendingDeletion(final ProcessMetadataValue metadata) {
    engine.stop();
    engine.writeRecords(
        RecordToWrite.event()
            .key(metadata.getProcessDefinitionKey())
            .process(
                ProcessIntent.DELETING,
                new ProcessRecord()
                    .setKey(metadata.getProcessDefinitionKey())
                    .setBpmnProcessId(metadata.getBpmnProcessId())
                    .setVersion(metadata.getVersion())
                    .setResourceName(metadata.getResourceName())
                    .setTenantId(metadata.getTenantId())));
    engine.start();

    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DELETING)
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .await();
  }
}
