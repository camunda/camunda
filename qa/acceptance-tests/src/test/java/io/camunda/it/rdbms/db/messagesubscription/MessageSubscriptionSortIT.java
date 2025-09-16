/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.messagesubscription;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.MessageSubscriptionDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.fixtures.MessageSubscriptionFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.sort.MessageSubscriptionSort;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class MessageSubscriptionSortIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByMessageNameAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.messageName().asc(),
        Comparator.comparing(MessageSubscriptionEntity::messageName));
  }

  @TestTemplate
  public void shouldSortByMessageNameDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.messageName().desc(),
        Comparator.comparing(MessageSubscriptionEntity::messageName).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<MessageSubscriptionSort.Builder, ObjectBuilder<MessageSubscriptionSort>>
          sortBuilder,
      final Comparator<MessageSubscriptionEntity> comparator) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final MessageSubscriptionDbReader reader = rdbmsService.getMessageSubscriptionReader();

    final var messageName = nextStringId();
    MessageSubscriptionFixtures.createAndSaveRandomMessageSubscriptions(
        rdbmsWriter, b -> b.messageName(messageName));

    final var searchResult =
        reader
            .search(
                new MessageSubscriptionQuery(
                    new MessageSubscriptionFilter.Builder().messageNames(messageName).build(),
                    MessageSubscriptionSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
