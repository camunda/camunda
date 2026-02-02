/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class DeleteDecisionInstanceBatchExecutorTest extends AbstractBatchOperationTest {

  private static final String DMN_RESOURCE = "/dmn/decision-table.dmn";
  private static final String DECISION_ID = "jedi_or_sith";

  @Test
  public void shouldDeleteDecisionInstance() {
    // given
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, "admin");

    // deploy the DRG
    engine
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .deploy()
        .getValue()
        .getDecisionRequirementsMetadata()
        .getFirst()
        .getDecisionRequirementsKey();

    // evaluate the decision
    final var decisionKey =
        engine
            .decision()
            .ofDecisionId(DECISION_ID)
            .withVariable("lightsaberColor", "blue")
            .evaluate()
            .getKey();

    // when
    final var batchOperationKey =
        createDeleteDecisionInstanceBatchOperation(List.of(decisionKey), claims);

    // then verify history deletion
    // TODO uncomment this assertion when the DELETE command gets appended
    // https://github.com/camunda/camunda/issues/42400
    //
    // assertThat(RecordingExporter.historyDeletionRecords().withResourceKey(decisionKey).limit(2))
    //        .hasSize(2)
    //        .extracting(
    //            Record::getIntent,
    //            r -> r.getValue().getResourceKey(),
    //            r -> r.getValue().getResourceType())
    //        .containsExactly(
    //            tuple(HistoryDeletionIntent.DELETE, decisionKey,
    // HistoryDeletionType.DECISION_INSTANCE),
    //            tuple(
    //                HistoryDeletionIntent.DELETED, decisionKey,
    // HistoryDeletionType.DECISION_INSTANCE));

    // then verify batch operation completed
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);
  }
}
