/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.messagesubscription;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.MessageSubscriptionDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.fixtures.MessageSubscriptionFixtures;
import io.camunda.it.rdbms.db.util.RdbmsTestConfiguration;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.filter.MessageSubscriptionFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.sort.MessageSubscriptionSort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@Tag("rdbms")
@DataJdbcTest
@ContextConfiguration(classes = {RdbmsTestConfiguration.class, RdbmsConfiguration.class})
@AutoConfigurationPackage
@TestPropertySource(
    properties = {
      "spring.liquibase.enabled=false",
      "camunda.data.secondary-storage.type=rdbms",
      "logging.level.io.camunda.db.rdbms=DEBUG"
    })
public class MessageSubscriptionSpecificFilterIT {
  private static final Long MESSAGE_SUBSCRIPTION_KEY = CommonFixtures.nextKey();
  private static final String MESSAGE_NAME = "messageName";
  private static final String TENANT_ID = "tenantId";
  private static final String CORRELATION_KEY = UUID.randomUUID().toString();
  private static final String FLOW_NODE_ID = CommonFixtures.nextStringId();
  private static final String PROCESS_DEFINITION_ID = CommonFixtures.nextStringId();
  private static final Long PROCESS_INSTANCE_KEY = CommonFixtures.nextKey();

  @Autowired private RdbmsService rdbmsService;

  @Autowired private MessageSubscriptionDbReader messageSubscriptionDbReader;

  private RdbmsWriters rdbmsWriters;

  @BeforeEach
  public void beforeAll() {
    rdbmsWriters = rdbmsService.createWriter(0L);
  }

  @ParameterizedTest
  @MethodSource("shouldFindWithSpecificFilterParameters")
  public void shouldFindWithSpecificFilter(final MessageSubscriptionFilter filter) {
    MessageSubscriptionFixtures.createAndSaveRandomMessageSubscriptions(rdbmsWriters);
    MessageSubscriptionFixtures.createAndSaveMessageSubscription(
        rdbmsWriters,
        MessageSubscriptionFixtures.createRandomized(
            b ->
                b.messageSubscriptionKey(MESSAGE_SUBSCRIPTION_KEY)
                    .messageName(MESSAGE_NAME)
                    .tenantId(TENANT_ID)
                    .correlationKey(CORRELATION_KEY)
                    .flowNodeId(FLOW_NODE_ID)
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .processInstanceKey(PROCESS_INSTANCE_KEY)
                    .messageSubscriptionState(MessageSubscriptionState.CORRELATED)));

    final var searchResult =
        messageSubscriptionDbReader.search(
            new MessageSubscriptionQuery(
                filter,
                MessageSubscriptionSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(5))));

    assertThat(searchResult.total()).isEqualTo(1);
    assertThat(searchResult.items()).hasSize(1);
    assertThat(searchResult.items().getFirst().messageSubscriptionKey())
        .isEqualTo(MESSAGE_SUBSCRIPTION_KEY);
  }

  static List<MessageSubscriptionFilter> shouldFindWithSpecificFilterParameters() {
    return List.of(
        new MessageSubscriptionFilter.Builder()
            .messageSubscriptionKeys(MESSAGE_SUBSCRIPTION_KEY)
            .build(),
        new MessageSubscriptionFilter.Builder().messageNames(MESSAGE_NAME).build(),
        new MessageSubscriptionFilter.Builder()
            .messageNameOperations(Operation.eq(MESSAGE_NAME))
            .build(),
        new MessageSubscriptionFilter.Builder()
            .messageSubscriptionStates(MessageSubscriptionState.CORRELATED.name())
            .build(),
        new MessageSubscriptionFilter.Builder()
            .messageSubscriptionStateOperations(
                Operation.eq(MessageSubscriptionState.CORRELATED.name()))
            .build(),
        new MessageSubscriptionFilter.Builder().correlationKeys(CORRELATION_KEY).build(),
        new MessageSubscriptionFilter.Builder()
            .correlationKeyOperations(Operation.eq(CORRELATION_KEY))
            .build(),
        new MessageSubscriptionFilter.Builder().flowNodeIds(FLOW_NODE_ID).build(),
        new MessageSubscriptionFilter.Builder()
            .flowNodeIdOperations(Operation.eq(FLOW_NODE_ID))
            .build(),
        new MessageSubscriptionFilter.Builder().processDefinitionIds(PROCESS_DEFINITION_ID).build(),
        new MessageSubscriptionFilter.Builder()
            .processDefinitionIdOperations(Operation.eq(PROCESS_DEFINITION_ID))
            .build(),
        new MessageSubscriptionFilter.Builder().processInstanceKeys(PROCESS_INSTANCE_KEY).build(),
        new MessageSubscriptionFilter.Builder()
            .processInstanceKeyOperations(Operation.eq(PROCESS_INSTANCE_KEY))
            .build(),
        new MessageSubscriptionFilter.Builder().tenantIds(TENANT_ID).build(),
        new MessageSubscriptionFilter.Builder()
            .tenantIdOperations(Operation.eq(TENANT_ID))
            .build());
  }
}
