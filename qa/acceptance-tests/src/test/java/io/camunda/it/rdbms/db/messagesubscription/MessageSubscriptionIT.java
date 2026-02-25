/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.messagesubscription;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.MessageSubscriptionDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.it.rdbms.db.fixtures.MessageSubscriptionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.sort.MessageSubscriptionSort;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class MessageSubscriptionIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();

  @TestTemplate
  public void shouldSaveAndFindById(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final MessageSubscriptionDbReader messageSubscriptionReader =
        rdbmsService.getMessageSubscriptionReader();

    final var subscriptionDbModel = MessageSubscriptionFixtures.createRandomized(b -> b);
    MessageSubscriptionFixtures.createAndSaveMessageSubscription(rdbmsWriters, subscriptionDbModel);

    final var instance =
        messageSubscriptionReader
            .findOne(subscriptionDbModel.messageSubscriptionKey())
            .orElse(null);

    compareMessageSubscriptions(instance, subscriptionDbModel);
  }

  @TestTemplate
  public void shouldSaveAndUpdate(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final MessageSubscriptionDbReader messageSubscriptionReader =
        rdbmsService.getMessageSubscriptionReader();

    final var subscription = MessageSubscriptionFixtures.createRandomized(b -> b);
    MessageSubscriptionFixtures.createAndSaveMessageSubscription(rdbmsWriters, subscription);

    final var roleUpdate =
        MessageSubscriptionFixtures.createRandomized(
            b ->
                b.messageSubscriptionKey(subscription.messageSubscriptionKey())
                    .rootProcessInstanceKey(subscription.rootProcessInstanceKey()));
    rdbmsWriters.getMessageSubscriptionWriter().update(roleUpdate);
    rdbmsWriters.flush();

    final var instance =
        messageSubscriptionReader.findOne(subscription.messageSubscriptionKey()).orElse(null);

    compareMessageSubscriptions(instance, roleUpdate);
  }

  @TestTemplate
  public void shouldFindAllPaged(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final MessageSubscriptionDbReader messageSubscriptionReader =
        rdbmsService.getMessageSubscriptionReader();

    final var messageName = "message-" + UUID.randomUUID();

    MessageSubscriptionFixtures.createAndSaveRandomMessageSubscriptions(
        rdbmsWriters, b -> b.messageName(messageName));

    final var searchResult =
        messageSubscriptionReader.search(
            new MessageSubscriptionQuery(
                new MessageSubscriptionFilter.Builder().messageNames(messageName).build(),
                MessageSubscriptionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllPagedWithHasMoreHits(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final MessageSubscriptionDbReader messageSubscriptionReader =
        rdbmsService.getMessageSubscriptionReader();

    final var messageName = "message-more-" + UUID.randomUUID();
    MessageSubscriptionFixtures.createAndSaveRandomMessageSubscriptions(
        rdbmsWriters, 120, b -> b.messageName(messageName));

    final var searchResult =
        messageSubscriptionReader.search(
            new MessageSubscriptionQuery(
                new MessageSubscriptionFilter.Builder().messageNames(messageName).build(),
                MessageSubscriptionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldDeleteProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final MessageSubscriptionDbReader reader = rdbmsService.getMessageSubscriptionReader();

    final var subscription1 =
        MessageSubscriptionFixtures.createAndSaveMessageSubscription(rdbmsWriters, b -> b);
    final var subscription2 =
        MessageSubscriptionFixtures.createAndSaveMessageSubscription(rdbmsWriters, b -> b);
    final var subscription3 =
        MessageSubscriptionFixtures.createAndSaveMessageSubscription(rdbmsWriters, b -> b);

    final MessageSubscriptionQuery query =
        MessageSubscriptionQuery.of(
            b ->
                b.filter(
                        f ->
                            f.processInstanceKeys(
                                subscription1.processInstanceKey(),
                                subscription2.processInstanceKey(),
                                subscription3.processInstanceKey()))
                    .sort(s -> s)
                    .page(p -> p.from(0).size(20)));

    var searchResult = reader.search(query);
    assertThat(searchResult.total()).isEqualTo(3);
    assertThat(searchResult.items()).hasSize(3);
    assertThat(searchResult.items().stream().map(MessageSubscriptionEntity::processInstanceKey))
        .containsExactlyInAnyOrder(
            subscription1.processInstanceKey(),
            subscription2.processInstanceKey(),
            subscription3.processInstanceKey());

    // when
    final int deleted =
        rdbmsWriters
            .getMessageSubscriptionWriter()
            .deleteProcessInstanceRelatedData(List.of(subscription2.processInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);

    searchResult = reader.search(query);

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(MessageSubscriptionEntity::processInstanceKey))
        .containsExactlyInAnyOrder(
            subscription1.processInstanceKey(), subscription3.processInstanceKey());
  }

  @TestTemplate
  public void shouldDeleteRootProcessInstanceRelatedData(
      final CamundaRdbmsTestApplication testApplication) {
    // given
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final MessageSubscriptionDbReader reader = rdbmsService.getMessageSubscriptionReader();

    final var subscription1 =
        MessageSubscriptionFixtures.createAndSaveMessageSubscription(rdbmsWriters, b -> b);
    final var subscription2 =
        MessageSubscriptionFixtures.createAndSaveMessageSubscription(rdbmsWriters, b -> b);
    final var subscription3 =
        MessageSubscriptionFixtures.createAndSaveMessageSubscription(rdbmsWriters, b -> b);

    final MessageSubscriptionQuery query =
        MessageSubscriptionQuery.of(
            b ->
                b.filter(
                        f ->
                            f.processInstanceKeys(
                                subscription1.processInstanceKey(),
                                subscription2.processInstanceKey(),
                                subscription3.processInstanceKey()))
                    .sort(s -> s)
                    .page(p -> p.from(0).size(20)));

    var searchResult = reader.search(query);
    assertThat(searchResult.total()).isEqualTo(3);
    assertThat(searchResult.items()).hasSize(3);
    assertThat(searchResult.items().stream().map(MessageSubscriptionEntity::rootProcessInstanceKey))
        .containsExactlyInAnyOrder(
            subscription1.rootProcessInstanceKey(),
            subscription2.rootProcessInstanceKey(),
            subscription3.rootProcessInstanceKey());

    // when
    final int deleted =
        rdbmsWriters
            .getMessageSubscriptionWriter()
            .deleteRootProcessInstanceRelatedData(
                List.of(subscription2.rootProcessInstanceKey()), 10);

    // then
    assertThat(deleted).isEqualTo(1);

    searchResult = reader.search(query);

    assertThat(searchResult.total()).isEqualTo(2);
    assertThat(searchResult.items()).hasSize(2);
    assertThat(searchResult.items().stream().map(MessageSubscriptionEntity::rootProcessInstanceKey))
        .containsExactlyInAnyOrder(
            subscription1.rootProcessInstanceKey(), subscription3.rootProcessInstanceKey());
  }

  private static void compareMessageSubscriptions(
      final MessageSubscriptionEntity instance, final MessageSubscriptionDbModel role) {
    assertThat(instance).isNotNull();
    assertThat(instance).usingRecursiveComparison().isEqualTo(role);
  }
}
