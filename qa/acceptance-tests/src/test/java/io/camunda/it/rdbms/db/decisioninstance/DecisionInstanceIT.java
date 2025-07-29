/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.decisioninstance;

import static io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures.createAndSaveDecisionInstance;
import static io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures.createAndSaveRandomDecisionInstance;
import static io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures.createAndSaveRandomDecisionInstances;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.DecisionInstanceDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.DecisionDefinitionFixtures;
import io.camunda.it.rdbms.db.fixtures.DecisionInstanceFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.sort.DecisionInstanceSort;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var original = DecisionInstanceFixtures.createRandomized(b -> b);
    createAndSaveDecisionInstance(rdbmsWriter, original);
    final var actual = decisionInstanceReader.findOne(original.decisionInstanceId()).orElseThrow();

    assertThat(actual).isNotNull();
    assertThat(actual.decisionInstanceId()).isEqualTo(original.decisionInstanceId());
    assertThat(actual.decisionInstanceKey()).isEqualTo(original.decisionInstanceKey());
    assertThat(actual.processDefinitionKey()).isEqualTo(original.processDefinitionKey());
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
  public void shouldFindAllDecisionInstancePaged(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var decisionDefinition =
        DecisionDefinitionFixtures.createAndSaveRandomDecisionDefinition(rdbmsWriter, b -> b);
    createAndSaveRandomDecisionInstances(
        rdbmsWriter, b -> b.decisionDefinitionKey(decisionDefinition.decisionDefinitionKey()));

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
  public void shouldFindDecisionInstanceWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var decisionDefinition =
        DecisionDefinitionFixtures.createAndSaveRandomDecisionDefinition(rdbmsWriter, b -> b);
    createAndSaveRandomDecisionInstances(rdbmsWriter);
    final var instance =
        createAndSaveRandomDecisionInstance(
            rdbmsWriter,
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
  public void shouldFindDecisionInstanceWithSearchAfter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var decisionDefinition =
        DecisionDefinitionFixtures.createAndSaveRandomDecisionDefinition(rdbmsWriter, b -> b);
    createAndSaveRandomDecisionInstances(
        rdbmsWriter,
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
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader decisionInstanceReader =
        rdbmsService.getDecisionInstanceReader();

    final var original =
        DecisionInstanceFixtures.createRandomized(
            b -> b.evaluationFailureMessage("x".repeat(9000)));
    createAndSaveDecisionInstance(rdbmsWriter, original);
    final var actual = decisionInstanceReader.findOne(original.decisionInstanceId()).orElseThrow();

    assertThat(actual).isNotNull();
    assertThat(actual.evaluationFailureMessage().length()).isEqualTo(4000);
  }

  @TestTemplate
  public void shouldCleanup(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final DecisionInstanceDbReader reader = rdbmsService.getDecisionInstanceReader();

    final var cleanupDate = NOW.minusDays(1);

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriter, b -> b);
    final var item1 =
        createAndSaveRandomDecisionInstance(
            rdbmsWriter, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        createAndSaveRandomDecisionInstance(
            rdbmsWriter, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        createAndSaveRandomDecisionInstance(
            rdbmsWriter, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // set cleanup dates
    rdbmsWriter
        .getDecisionInstanceWriter()
        .scheduleForHistoryCleanup(item1.processInstanceKey(), NOW);
    rdbmsWriter
        .getDecisionInstanceWriter()
        .scheduleForHistoryCleanup(item2.processInstanceKey(), NOW.minusDays(2));
    rdbmsWriter.flush();

    // cleanup
    rdbmsWriter.getDecisionInstanceWriter().cleanupHistory(PARTITION_ID, cleanupDate, 10);

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
}
