/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisioninstance;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.generateRandomString;
import static io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures.createAndSaveDecisionInstance;
import static io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures.createAndSaveRandomDecisionInstance;
import static io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures.createAndSaveRandomDecisionInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedInput;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedOutput;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.sort.DecisionInstanceSort;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.sql.DataSource;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class DecisionInstanceIT {
  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindDecisionInstanceById(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var original = DecisionInstanceFixtures.createRandomized(b -> b);
    createAndSaveDecisionInstance(rdbmsWriters, original);
    final var actual = decisionInstanceReader.findOne(original.decisionInstanceId()).orElseThrow();

    assertThat(actual).isNotNull();
    assertThat(actual.decisionInstanceId()).isEqualTo(original.decisionInstanceId());
    assertThat(actual.decisionInstanceKey()).isEqualTo(original.decisionInstanceKey());
    assertThat(actual.processDefinitionKey()).isEqualTo(original.processDefinitionKey());
    assertThat(actual.processInstanceKey()).isEqualTo(original.processInstanceKey());
    assertThat(actual.rootProcessInstanceKey()).isEqualTo(original.rootProcessInstanceKey());
    assertThat(actual.decisionDefinitionId()).isEqualTo(original.decisionDefinitionId());
    assertThat(actual.state()).isEqualTo(original.state());
    assertThat(actual.evaluationDate())
        .isCloseTo(original.evaluationDate(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(actual.evaluationFailure()).isEqualTo(original.evaluationFailure());
    assertThat(actual.result()).isEqualTo(original.result());
    assertThat(actual.decisionDefinitionType()).isEqualTo(original.decisionType());
    assertThat(actual.evaluatedInputs()).hasSize(original.evaluatedInputs().size());
    assertThat(actual.evaluatedInputs()).hasSize(original.evaluatedInputs().size());
    assertThat(actual.evaluatedOutputs()).hasSize(original.evaluatedOutputs().size());
  }

  @TestTemplate
  public void shouldSaveAndFindByAuthorizedResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var original = DecisionInstanceFixtures.createRandomized(b -> b);
    createAndSaveDecisionInstance(rdbmsWriters, original);
    createAndSaveRandomDecisionInstances(rdbmsWriters);

    final var searchResult =
        decisionInstanceReader.search(
            DecisionInstanceQuery.of(b -> b),
            CommonFixtures.resourceAccessChecksFromResourceIds(
                AuthorizationResourceType.DECISION_DEFINITION, original.decisionDefinitionId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();
    assertThat(instance.decisionInstanceKey()).isEqualTo(original.decisionInstanceKey());
  }

  @TestTemplate
  public void shouldSaveAndFindByAuthorizedTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var original = DecisionInstanceFixtures.createRandomized(b -> b);
    createAndSaveDecisionInstance(rdbmsWriters, original);
    createAndSaveRandomDecisionInstances(rdbmsWriters);

    final var searchResult =
        decisionInstanceReader.search(
            DecisionInstanceQuery.of(b -> b),
            CommonFixtures.resourceAccessChecksFromTenantIds(original.tenantId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);

    final var instance = searchResult.items().getFirst();
    assertThat(instance.decisionInstanceKey()).isEqualTo(original.decisionInstanceKey());
  }

  @TestTemplate
  public void shouldFindAllDecisionInstancePaged(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var decisionDefinition =
        DecisionDefinitionFixtures.createAndSaveRandomDecisionDefinition(rdbmsWriters, b -> b);
    createAndSaveRandomDecisionInstances(
        rdbmsWriters, b -> b.decisionDefinitionKey(decisionDefinition.decisionDefinitionKey()));

    final var searchResult =
        decisionInstanceReader.search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.decisionDefinitionKeys(
                                    decisionDefinition.decisionDefinitionKey()))
                        .sort(s -> s.evaluationDate().asc().decisionDefinitionName().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllDecisionInstancePagedWithHasMoreHits(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var decisionDefinition =
        DecisionDefinitionFixtures.createAndSaveRandomDecisionDefinition(rdbmsWriters, b -> b);
    createAndSaveRandomDecisionInstances(
        rdbmsWriters,
        120,
        b -> b.decisionDefinitionKey(decisionDefinition.decisionDefinitionKey()));

    final var searchResult =
        decisionInstanceReader.search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.decisionDefinitionKeys(
                                    decisionDefinition.decisionDefinitionKey()))
                        .sort(s -> s.evaluationDate().asc().decisionDefinitionName().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindDecisionInstanceWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var decisionDefinition =
        DecisionDefinitionFixtures.createAndSaveRandomDecisionDefinition(rdbmsWriters, b -> b);
    createAndSaveRandomDecisionInstances(rdbmsWriters);
    final var instance =
        createAndSaveRandomDecisionInstance(
            rdbmsWriters,
            b ->
                b.decisionDefinitionKey(decisionDefinition.decisionDefinitionKey())
                    .decisionDefinitionId(decisionDefinition.decisionDefinitionId()));
    final var searchResult =
        decisionInstanceReader.search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.decisionInstanceKeys(instance.decisionInstanceKey())
                                    .decisionDefinitionIds(instance.decisionDefinitionId())
                                    .decisionDefinitionNames(decisionDefinition.name())
                                    .processDefinitionKeys(instance.processDefinitionKey())
                                    .states(instance.state())
                                    .decisionTypes(instance.decisionType())
                                    .processInstanceKeys(instance.processInstanceKey())
                                    .businessIds(instance.businessId())
                                    .evaluationFailures(instance.evaluationFailure())
                                    .evaluationDateOperations(
                                        List.of(
                                            Operation.gt(
                                                instance.evaluationDate().minusSeconds(1)))))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().decisionInstanceId())
        .isEqualTo(instance.decisionInstanceId());
  }

  @TestTemplate
  public void shouldFindDecisionInstanceByBusinessIdEq(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final String businessId = generateRandomString("businessId");
    final var instance =
        createAndSaveRandomDecisionInstance(rdbmsWriters, b -> b.businessId(businessId));
    createAndSaveRandomDecisionInstances(rdbmsWriters);

    final var searchResult =
        decisionInstanceReader.search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(f -> f.businessIdOperations(Operation.eq(businessId)))
                        .page(p -> p.from(0).size(10))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().decisionInstanceId())
        .isEqualTo(instance.decisionInstanceId());
  }

  @TestTemplate
  public void shouldFindDecisionInstanceByBusinessIdLike(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final String prefix = generateRandomString("business-transaction");
    final var instance =
        createAndSaveRandomDecisionInstance(rdbmsWriters, b -> b.businessId(prefix + "-v1"));
    createAndSaveRandomDecisionInstances(rdbmsWriters);

    final var searchResult =
        decisionInstanceReader.search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(f -> f.businessIdOperations(Operation.like(prefix + "*")))
                        .page(p -> p.from(0).size(10))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().decisionInstanceId())
        .isEqualTo(instance.decisionInstanceId());
  }

  @TestTemplate
  public void shouldFindDecisionInstanceWithSearchAfter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var decisionDefinition =
        DecisionDefinitionFixtures.createAndSaveRandomDecisionDefinition(rdbmsWriters, b -> b);
    createAndSaveRandomDecisionInstances(
        rdbmsWriters,
        b ->
            b.decisionDefinitionKey(decisionDefinition.decisionDefinitionKey())
                .decisionDefinitionId(decisionDefinition.decisionDefinitionId()));
    final var sort =
        DecisionInstanceSort.of(
            s ->
                s.decisionDefinitionName()
                    .asc()
                    .decisionDefinitionVersion()
                    .asc()
                    .evaluationDate()
                    .desc());
    final var searchResult =
        decisionInstanceReader.search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(
                            f -> f.decisionDefinitionIds(decisionDefinition.decisionDefinitionId()))
                        .sort(sort)));

    final var firstPage =
        decisionInstanceReader.search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(
                            f -> f.decisionDefinitionIds(decisionDefinition.decisionDefinitionId()))
                        .sort(sort)
                        .page(p -> p.size(15))));

    final var nextPage =
        decisionInstanceReader.search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(
                            f -> f.decisionDefinitionIds(decisionDefinition.decisionDefinitionId()))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  @TestTemplate
  public void shouldSaveAndFindDecisionInstanceWithLargeFailureMessage(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var original =
        DecisionInstanceFixtures.createRandomized(
            b -> b.evaluationFailureMessage("x".repeat(9000)));
    createAndSaveDecisionInstance(rdbmsWriters, original);
    final var actual = decisionInstanceReader.findOne(original.decisionInstanceId()).orElseThrow();

    assertThat(actual).isNotNull();
    assertThat(actual.evaluationFailureMessage().length()).isEqualTo(4000);
  }

  @TestTemplate
  public void shouldDeleteProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader reader = rdbmsService.getDecisionInstanceReader();

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    final var item1 =
        createAndSaveRandomDecisionInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        createAndSaveRandomDecisionInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        createAndSaveRandomDecisionInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // when
    final int deleted =
        rdbmsWriters
            .getDecisionInstanceWriter()
            .deleteProcessInstanceRelatedData(List.of(item2.processInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        reader.search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(definition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(DecisionInstanceEntity::decisionInstanceKey))
        .containsExactlyInAnyOrder(item1.decisionInstanceKey(), item3.decisionInstanceKey());
  }

  @TestTemplate
  public void shouldDeleteRootProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader reader = rdbmsService.getDecisionInstanceReader();

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    final var item1 =
        createAndSaveRandomDecisionInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        createAndSaveRandomDecisionInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        createAndSaveRandomDecisionInstance(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // when
    final int deleted =
        rdbmsWriters
            .getDecisionInstanceWriter()
            .deleteRootProcessInstanceRelatedData(List.of(item2.rootProcessInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        reader.search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(definition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(DecisionInstanceEntity::decisionInstanceKey))
        .containsExactlyInAnyOrder(item1.decisionInstanceKey(), item3.decisionInstanceKey());
  }

  @TestTemplate
  public void shouldSaveDecisionInstancesWithLargeInputOutputsAndResultsAndFindDecisionInstanceById(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var largeResult = "x".repeat(9_000);
    final var largeOutput = "y".repeat(10_000);
    final var largeInput = "z".repeat(10_000);

    final var decisionInstanceKey = DecisionInstanceFixtures.nextKey();
    final var decisionInstanceId = decisionInstanceKey + "-1";
    final var evaluatedOutputId = DecisionInstanceFixtures.nextStringId();
    final var evaluatedInputId = DecisionInstanceFixtures.nextStringId();

    final var original =
        DecisionInstanceFixtures.createRandomized(
            b ->
                b.decisionInstanceId(decisionInstanceId)
                    .decisionInstanceKey(decisionInstanceKey)
                    .result(largeResult)
                    .evaluatedOutputs(
                        List.of(
                            new EvaluatedOutput(
                                decisionInstanceId,
                                evaluatedOutputId,
                                "outputName",
                                largeOutput,
                                "ruleId",
                                123)))
                    .evaluatedInputs(
                        List.of(
                            new EvaluatedInput(
                                decisionInstanceId, evaluatedInputId, "inputName", largeInput))));
    createAndSaveDecisionInstance(rdbmsWriters, original);
    final var actual = decisionInstanceReader.findOne(decisionInstanceId).orElseThrow();

    assertThat(actual).isNotNull();
    assertThat(actual.decisionInstanceId()).isEqualTo(decisionInstanceId);
    assertThat(actual.decisionInstanceKey()).isEqualTo(decisionInstanceKey);
    assertThat(actual.result()).isEqualTo(largeResult);
    assertThat(actual.evaluatedOutputs())
        .isEqualTo(
            List.of(
                new DecisionInstanceOutputEntity(
                    evaluatedOutputId, "outputName", largeOutput, "ruleId", 123)));
    assertThat(actual.evaluatedInputs())
        .isEqualTo(
            List.of(new DecisionInstanceInputEntity(evaluatedInputId, "inputName", largeInput)));
  }

  @TestTemplate
  public void shouldFallbackToOldColumnsWhenFindingDecisionInstanceById(
      final CamundaRdbmsTestApplication testApplication) throws SQLException {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var decisionInstanceKey = DecisionInstanceFixtures.nextKey();
    final var decisionInstanceId = decisionInstanceKey + "-1";
    final var evaluatedOutputId = DecisionInstanceFixtures.nextStringId();
    final var evaluatedInputId = DecisionInstanceFixtures.nextStringId();

    final var original =
        DecisionInstanceFixtures.createRandomized(
            b ->
                b.decisionInstanceId(decisionInstanceId)
                    .decisionInstanceKey(decisionInstanceKey)
                    .result("fullResult")
                    .evaluatedOutputs(
                        List.of(
                            new EvaluatedOutput(
                                decisionInstanceId,
                                evaluatedOutputId,
                                "outputName",
                                "fullOutput",
                                "ruleId",
                                123)))
                    .evaluatedInputs(
                        List.of(
                            new EvaluatedInput(
                                decisionInstanceId, evaluatedInputId, "inputName", "fullInput"))));
    createAndSaveDecisionInstance(rdbmsWriters, original);

    // manually change values in DB, so we can verify fallback behaviour for
    // data written in older versions
    final var oldColumnResult = "oldColumnResult";
    final var oldColumnOutput = "oldColumnOutput";
    final var oldColumnInput = "oldColumnInput";
    final var dataSource = testApplication.bean(DataSource.class);

    try (final var connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      update(
          connection,
          "UPDATE DECISION_INSTANCE SET FULL_RESULT = NULL, RESULT = ? WHERE DECISION_INSTANCE_ID = ?",
          oldColumnResult,
          decisionInstanceId);
      update(
          connection,
          "UPDATE DECISION_INSTANCE_OUTPUT SET FULL_OUTPUT_VALUE = NULL, OUTPUT_VALUE = ? WHERE DECISION_INSTANCE_ID = ? AND OUTPUT_ID = ?",
          oldColumnOutput,
          decisionInstanceId,
          evaluatedOutputId);
      update(
          connection,
          "UPDATE DECISION_INSTANCE_INPUT SET FULL_INPUT_VALUE = NULL, INPUT_VALUE = ? WHERE DECISION_INSTANCE_ID = ? AND INPUT_ID = ?",
          oldColumnInput,
          decisionInstanceId,
          evaluatedInputId);
      connection.commit();
    }

    final var actual = decisionInstanceReader.findOne(decisionInstanceId).orElseThrow();

    assertThat(actual).isNotNull();
    assertThat(actual.decisionInstanceId()).isEqualTo(decisionInstanceId);
    assertThat(actual.decisionInstanceKey()).isEqualTo(decisionInstanceKey);
    assertThat(actual.result()).isEqualTo(oldColumnResult);
    assertThat(actual.evaluatedOutputs())
        .isEqualTo(
            List.of(
                new DecisionInstanceOutputEntity(
                    evaluatedOutputId, "outputName", oldColumnOutput, "ruleId", 123)));
    assertThat(actual.evaluatedInputs())
        .isEqualTo(
            List.of(
                new DecisionInstanceInputEntity(evaluatedInputId, "inputName", oldColumnInput)));
  }

  private void update(final Connection connection, final String sql, final String... params)
      throws SQLException {
    try (final var pstmt = connection.prepareStatement(sql)) {
      for (var i = 0; i < params.length; i++) {
        pstmt.setString(i + 1, params[i]);
      }
      pstmt.executeUpdate();
    }
  }
}
