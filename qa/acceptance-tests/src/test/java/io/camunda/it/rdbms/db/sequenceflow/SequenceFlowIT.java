/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.sequenceflow;

import static io.camunda.it.rdbms.db.fixtures.SequenceFlowFixtures.createAndSaveRandomSequenceFlow;
import static io.camunda.it.rdbms.db.fixtures.SequenceFlowFixtures.createAndSaveRandomSequenceFlows;
import static io.camunda.it.rdbms.db.fixtures.SequenceFlowFixtures.createRandomized;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.query.SequenceFlowQuery;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class SequenceFlowIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldCreateSequenceFlow(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();

    // when
    final var sequenceFlow = createAndSaveRandomSequenceFlow(rdbmsWriter, b -> b);

    // then
    final var items =
        sequenceFlowReader
            .search(
                SequenceFlowQuery.of(
                    q -> q.filter(f -> f.processInstanceKey(sequenceFlow.processInstanceKey()))))
            .items();
    assertThat(items)
        .containsExactly(
            new SequenceFlowEntity.Builder()
                .sequenceFlowId(sequenceFlow.processInstanceKey() + "_" + sequenceFlow.flowNodeId())
                .flowNodeId(sequenceFlow.flowNodeId())
                .processInstanceKey(sequenceFlow.processInstanceKey())
                .processDefinitionKey(sequenceFlow.processDefinitionKey())
                .processDefinitionId(sequenceFlow.processDefinitionId())
                .tenantId(sequenceFlow.tenantId())
                .build());
  }

  @TestTemplate
  public void shouldSearchSequenceFlow(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();

    final var sequenceFlow = createAndSaveRandomSequenceFlow(rdbmsWriter, b -> b);
    createAndSaveRandomSequenceFlows(rdbmsWriter);

    // when
    final var actual =
        sequenceFlowReader
            .search(
                SequenceFlowQuery.of(
                    q -> q.filter(f -> f.processInstanceKey(sequenceFlow.processInstanceKey()))))
            .items();

    // then
    assertThat(actual)
        .containsExactly(
            new SequenceFlowEntity.Builder()
                .sequenceFlowId(sequenceFlow.processInstanceKey() + "_" + sequenceFlow.flowNodeId())
                .flowNodeId(sequenceFlow.flowNodeId())
                .processInstanceKey(sequenceFlow.processInstanceKey())
                .processDefinitionKey(sequenceFlow.processDefinitionKey())
                .processDefinitionId(sequenceFlow.processDefinitionId())
                .tenantId(sequenceFlow.tenantId())
                .build());
  }

  @TestTemplate
  public void shouldFindSequenceFlowByAuthorizedResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();

    final var sequenceFlow =
        createAndSaveRandomSequenceFlow(rdbmsWriter, b -> b.processInstanceKey(42L));
    createAndSaveRandomSequenceFlows(rdbmsWriter, b -> b.processInstanceKey(42L));

    // when
    final var searchResult =
        sequenceFlowReader.search(
            SequenceFlowQuery.of(q -> q.filter(f -> f.processInstanceKey(42L))),
            CommonFixtures.resourceAccessChecksFromResourceIds(sequenceFlow.processDefinitionId()));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processInstanceKey())
        .isEqualTo(sequenceFlow.processInstanceKey());
  }

  @TestTemplate
  public void shouldFindSequenceFlowByAuthorizedTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();

    final var sequenceFlow =
        createAndSaveRandomSequenceFlow(rdbmsWriter, b -> b.processInstanceKey(42L));
    createAndSaveRandomSequenceFlows(rdbmsWriter, b -> b.processInstanceKey(42L));

    // when
    final var searchResult =
        sequenceFlowReader.search(
            SequenceFlowQuery.of(q -> q.filter(f -> f.processInstanceKey(42L))),
            CommonFixtures.resourceAccessChecksFromTenantIds(sequenceFlow.tenantId()));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().processInstanceKey())
        .isEqualTo(sequenceFlow.processInstanceKey());
  }

  @TestTemplate
  public void shouldCreateSequenceFlowIfNotExists(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();
    final var sequenceFlowWriter = rdbmsWriter.getSequenceFlowWriter();

    final var sequenceFlow = createRandomized(b -> b);
    // when
    sequenceFlowWriter.createIfNotExists(sequenceFlow);
    rdbmsWriter.flush();

    // then
    final var items =
        sequenceFlowReader
            .search(
                SequenceFlowQuery.of(
                    q -> q.filter(f -> f.processInstanceKey(sequenceFlow.processInstanceKey()))))
            .items();
    assertThat(items.getFirst().processInstanceKey()).isEqualTo(sequenceFlow.processInstanceKey());
  }

  @TestTemplate
  public void shouldNotCreateSequenceFlowIfExists(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();
    final var sequenceFlowWriter = rdbmsWriter.getSequenceFlowWriter();
    final var dbModel = createRandomized(b -> b);
    sequenceFlowWriter.create(dbModel);
    rdbmsWriter.flush();

    // when
    sequenceFlowWriter.createIfNotExists(dbModel);
    rdbmsWriter.flush();

    // then
    final var items =
        sequenceFlowReader
            .search(
                SequenceFlowQuery.of(
                    q -> q.filter(f -> f.processInstanceKey(dbModel.processInstanceKey()))))
            .items();
    assertThat(items.getFirst().processInstanceKey()).isEqualTo(dbModel.processInstanceKey());
  }

  @TestTemplate
  public void shouldDeleteSequenceFlow(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var sequenceFlowReader = rdbmsService.getSequenceFlowReader();
    final var sequenceFlowWriter = rdbmsWriter.getSequenceFlowWriter();

    final var dbModel = createRandomized(b -> b);
    sequenceFlowWriter.create(dbModel);
    rdbmsWriter.flush();

    assertThat(
            sequenceFlowReader
                .search(
                    SequenceFlowQuery.of(
                        q -> q.filter(f -> f.processInstanceKey(dbModel.processInstanceKey()))))
                .items())
        .isNotEmpty();

    // when
    sequenceFlowWriter.delete(dbModel);
    rdbmsWriter.flush();

    // then
    final var itemsAfterDelete =
        sequenceFlowReader
            .search(
                SequenceFlowQuery.of(
                    q -> q.filter(f -> f.processInstanceKey(dbModel.processInstanceKey()))))
            .items();
    assertThat(itemsAfterDelete).isEmpty();
  }
}
