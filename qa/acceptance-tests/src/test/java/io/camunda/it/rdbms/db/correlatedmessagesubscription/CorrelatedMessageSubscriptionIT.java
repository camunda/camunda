/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.correlatedmessagesubscription;

import static io.camunda.it.rdbms.db.fixtures.CorrelatedMessageSubscriptionFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.CorrelatedMessageSubscriptionDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.CorrelatedMessageSubscriptionDbModel;
import io.camunda.it.rdbms.db.fixtures.CorrelatedMessageSubscriptionFixtures;
import io.camunda.it.rdbms.db.fixtures.ProcessDefinitionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.search.query.CorrelatedMessageSubscriptionQuery;
import io.camunda.search.sort.CorrelatedMessageSubscriptionSort;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class CorrelatedMessageSubscriptionIT {

  public static final int PARTITION_ID = 0;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindCorrelatedMessageSubscriptionByCompositeKey(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final CorrelatedMessageSubscriptionDbReader correlatedMessageSubscriptionReader =
        rdbmsService.getCorrelatedMessageSubscriptionReader();

    final var original = CorrelatedMessageSubscriptionFixtures.createRandomized(b -> b);
    createAndSaveCorrelatedMessageSubscription(rdbmsWriters, original);

    final var instance =
        correlatedMessageSubscriptionReader
            .findOne(original.messageKey(), original.subscriptionKey())
            .orElse(null);

    compareCorrelatedMessageSubscription(instance, original);
  }

  @TestTemplate
  public void shouldFindCorrelatedMessageSubscriptionByProcessDefinitionId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final CorrelatedMessageSubscriptionDbReader correlatedMessageSubscriptionReader =
        rdbmsService.getCorrelatedMessageSubscriptionReader();

    final var original = CorrelatedMessageSubscriptionFixtures.createRandomized(b -> b);
    createAndSaveCorrelatedMessageSubscription(rdbmsWriters, original);

    final var searchResult =
        correlatedMessageSubscriptionReader.search(
            CorrelatedMessageSubscriptionQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(original.processDefinitionId()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(10))));

    assertThat(searchResult.items())
        .extracting(CorrelatedMessageSubscriptionEntity::processDefinitionId)
        .contains(original.processDefinitionId());
  }

  @TestTemplate
  public void shouldFindCorrelatedMessageSubscriptionByAuthorizedResourceId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final CorrelatedMessageSubscriptionDbReader reader =
        rdbmsService.getCorrelatedMessageSubscriptionReader();

    final var original = CorrelatedMessageSubscriptionFixtures.createRandomized(b -> b);
    createAndSaveCorrelatedMessageSubscription(rdbmsWriters, original);
    createAndSaveRandomCorrelatedMessageSubscriptions(rdbmsWriters);

    final var searchResult =
        reader.search(
            CorrelatedMessageSubscriptionQuery.of(b -> b),
            resourceAccessChecksFromResourceIds(
                AuthorizationResourceType.PROCESS_DEFINITION, original.processDefinitionId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    compareCorrelatedMessageSubscription(searchResult.items().getFirst(), original);
  }

  @TestTemplate
  public void shouldFindCorrelatedMessageSubscriptionByAuthorizedTenantId(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final CorrelatedMessageSubscriptionDbReader reader =
        rdbmsService.getCorrelatedMessageSubscriptionReader();

    final var original = CorrelatedMessageSubscriptionFixtures.createRandomized(b -> b);
    createAndSaveCorrelatedMessageSubscription(rdbmsWriters, original);
    createAndSaveRandomCorrelatedMessageSubscriptions(rdbmsWriters);

    final var searchResult =
        reader.search(
            CorrelatedMessageSubscriptionQuery.of(b -> b),
            resourceAccessChecksFromTenantIds(original.tenantId()));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    compareCorrelatedMessageSubscription(searchResult.items().getFirst(), original);
  }

  @TestTemplate
  public void shouldFindAllCorrelatedMessageSubscriptionsPaged(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final CorrelatedMessageSubscriptionDbReader reader =
        rdbmsService.getCorrelatedMessageSubscriptionReader();

    final String processDefinitionId = CorrelatedMessageSubscriptionFixtures.nextStringId();
    createAndSaveRandomCorrelatedMessageSubscriptions(
        rdbmsWriters, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        reader.search(
            CorrelatedMessageSubscriptionQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinitionId))
                        .sort(s -> s.correlationTime().asc().messageName().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllCorrelatedMessageSubscriptionsPagedWithHasMoreHits(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final CorrelatedMessageSubscriptionDbReader reader =
        rdbmsService.getCorrelatedMessageSubscriptionReader();

    final String processDefinitionId = CorrelatedMessageSubscriptionFixtures.nextStringId();
    createAndSaveRandomCorrelatedMessageSubscriptions(
        rdbmsWriters, 120, b -> b.processDefinitionId(processDefinitionId));

    final var searchResult =
        reader.search(
            CorrelatedMessageSubscriptionQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionIds(processDefinitionId))
                        .sort(s -> s.correlationTime().asc().messageName().asc())
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindCorrelatedMessageSubscriptionWithFullFilter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final CorrelatedMessageSubscriptionDbReader reader =
        rdbmsService.getCorrelatedMessageSubscriptionReader();

    final var original = CorrelatedMessageSubscriptionFixtures.createRandomized(b -> b);
    createAndSaveCorrelatedMessageSubscription(rdbmsWriters, original);
    createAndSaveRandomCorrelatedMessageSubscriptions(rdbmsWriters);

    final var searchResult =
        reader.search(
            CorrelatedMessageSubscriptionQuery.of(
                b ->
                    b.filter(
                            f ->
                                f.correlationKeys(original.correlationKey())
                                    .flowNodeIds(original.flowNodeId())
                                    .flowNodeInstanceKeys(original.flowNodeInstanceKey())
                                    .messageKeys(original.messageKey())
                                    .messageNames(original.messageName())
                                    .partitionIds(original.partitionId())
                                    .processInstanceKeys(original.processInstanceKey())
                                    .processDefinitionIds(original.processDefinitionId())
                                    .processDefinitionKeys(original.processDefinitionKey())
                                    .subscriptionKeys(original.subscriptionKey())
                                    .tenantIds(original.tenantId()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().messageKey()).isEqualTo(original.messageKey());
    assertThat(searchResult.items().getFirst().subscriptionKey())
        .isEqualTo(original.subscriptionKey());
  }

  @TestTemplate
  public void shouldFindCorrelatedMessageSubscriptionWithSearchAfter(
      final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final CorrelatedMessageSubscriptionDbReader reader =
        rdbmsService.getCorrelatedMessageSubscriptionReader();

    final var processDefinitionKey = nextKey();
    createAndSaveRandomCorrelatedMessageSubscriptions(
        rdbmsWriters, b -> b.processDefinitionKey(processDefinitionKey));
    final var sort = CorrelatedMessageSubscriptionSort.of(s -> s.flowNodeId().asc());
    final var searchResult =
        reader.search(
            CorrelatedMessageSubscriptionQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                        .sort(sort)
                        .page(p -> p.from(0).size(20))));

    final var firstPage =
        reader.search(
            CorrelatedMessageSubscriptionQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                        .sort(sort)
                        .page(p -> p.size(15))));

    final var nextPage =
        reader.search(
            CorrelatedMessageSubscriptionQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(processDefinitionKey))
                        .sort(sort)
                        .page(p -> p.size(5).after(firstPage.endCursor()))));

    assertThat(nextPage.total()).isEqualTo(20);
    assertThat(nextPage.items()).hasSize(5);
    assertThat(nextPage.items()).isEqualTo(searchResult.items().subList(15, 20));
  }

  @TestTemplate
  public void shouldDeleteProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final CorrelatedMessageSubscriptionDbReader reader =
        rdbmsService.getCorrelatedMessageSubscriptionReader();

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    final var item1 =
        CorrelatedMessageSubscriptionFixtures.createAndSaveCorrelatedMessageSubscription(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        CorrelatedMessageSubscriptionFixtures.createAndSaveCorrelatedMessageSubscription(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        CorrelatedMessageSubscriptionFixtures.createAndSaveCorrelatedMessageSubscription(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // when
    final int deleted =
        rdbmsWriters
            .getCorrelatedMessageSubscriptionWriter()
            .deleteProcessInstanceRelatedData(List.of(item2.processInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        reader.search(
            CorrelatedMessageSubscriptionQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(definition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(CorrelatedMessageSubscriptionEntity::messageKey))
        .containsExactlyInAnyOrder(item1.messageKey(), item3.messageKey());
  }

  @TestTemplate
  public void shouldDeleteRootProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final CorrelatedMessageSubscriptionDbReader reader =
        rdbmsService.getCorrelatedMessageSubscriptionReader();

    final var definition =
        ProcessDefinitionFixtures.createAndSaveProcessDefinition(rdbmsWriters, b -> b);
    final var item1 =
        CorrelatedMessageSubscriptionFixtures.createAndSaveCorrelatedMessageSubscription(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item2 =
        CorrelatedMessageSubscriptionFixtures.createAndSaveCorrelatedMessageSubscription(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));
    final var item3 =
        CorrelatedMessageSubscriptionFixtures.createAndSaveCorrelatedMessageSubscription(
            rdbmsWriters, b -> b.processDefinitionKey(definition.processDefinitionKey()));

    // when
    final int deleted =
        rdbmsWriters
            .getCorrelatedMessageSubscriptionWriter()
            .deleteRootProcessInstanceRelatedData(List.of(item2.rootProcessInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);
    final var searchResult =
        reader.search(
            CorrelatedMessageSubscriptionQuery.of(
                b ->
                    b.filter(f -> f.processDefinitionKeys(definition.processDefinitionKey()))
                        .sort(s -> s)
                        .page(p -> p.from(0).size(20))));

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(CorrelatedMessageSubscriptionEntity::messageKey))
        .containsExactlyInAnyOrder(item1.messageKey(), item3.messageKey());
  }

  private void compareCorrelatedMessageSubscription(
      final CorrelatedMessageSubscriptionEntity actual,
      final CorrelatedMessageSubscriptionDbModel expected) {
    assertThat(actual).isNotNull();
    assertThat(actual.correlationKey()).isEqualTo(expected.correlationKey());
    assertThat(actual.correlationTime())
        .isCloseTo(expected.correlationTime(), new TemporalUnitWithinOffset(1, ChronoUnit.MILLIS));
    assertThat(actual.flowNodeId()).isEqualTo(expected.flowNodeId());
    assertThat(actual.flowNodeInstanceKey()).isEqualTo(expected.flowNodeInstanceKey());
    assertThat(actual.messageKey()).isEqualTo(expected.messageKey());
    assertThat(actual.messageName()).isEqualTo(expected.messageName());
    assertThat(actual.partitionId()).isEqualTo(expected.partitionId());
    assertThat(actual.processDefinitionId()).isEqualTo(expected.processDefinitionId());
    assertThat(actual.processDefinitionKey()).isEqualTo(expected.processDefinitionKey());
    assertThat(actual.processInstanceKey()).isEqualTo(expected.processInstanceKey());
    assertThat(actual.rootProcessInstanceKey()).isEqualTo(expected.rootProcessInstanceKey());
    assertThat(actual.subscriptionKey()).isEqualTo(expected.subscriptionKey());
    assertThat(actual.tenantId()).isEqualTo(expected.tenantId());
  }
}
