/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

final class AuthorizationHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-authorizations";
  private final AuthorizationHandler underTest = new AuthorizationHandler(indexName);

  @Test
  void shouldGenerateIds() {
    // given
    final var authorizationRecordValue = factory.generateObject(AuthorizationRecordValue.class);

    final var authorizationRecord =
        factory.<AuthorizationRecordValue>generateRecord(
            ValueType.AUTHORIZATION, r -> r.withValue(authorizationRecordValue));

    // when
    final var idList = underTest.generateIds(authorizationRecord);

    // then
    assertThat(idList)
        .containsExactly(
            authorizationRecordValue.getOwnerKey()
                + "-"
                + authorizationRecordValue.getResourceType());
  }
}
