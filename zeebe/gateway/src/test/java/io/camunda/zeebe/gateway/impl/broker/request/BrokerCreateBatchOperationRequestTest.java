/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationAuthorizationCheck;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BrokerCreateBatchOperationRequestTest {

  /**
   * The gateway copies a RequiredAuthorization into a BatchOperationAuthorizationCheck by hand, so
   * a component added to RequiredAuthorization is dropped silently and the engine checks a weaker
   * requirement than the gateway asked for. This fails when that happens: decide how the new
   * component crosses the wire, then update the check, both conversions and this list.
   */
  @Test
  void shouldKnowEveryComponentOfRequiredAuthorization() {
    // when
    final var components =
        Arrays.stream(RequiredAuthorization.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    // then
    assertThat(components)
        .containsExactly(
            "resourceType",
            "permissionType",
            "resourceIds",
            // not part of the wire format: functions evaluated against a runtime document
            "resourceIdSupplier",
            "resourcePropertyNames",
            // not part of the wire format: functions evaluated against a runtime document
            "condition",
            "transitive");
  }

  @Test
  void shouldWriteTheAuthorizationAsACheck() {
    // given
    final var authorization =
        new RequiredAuthorization<>(
            AuthorizationResourceType.PROCESS_DEFINITION,
            PermissionType.UPDATE_PROCESS_INSTANCE,
            List.of("a", "b"),
            null,
            Set.of("assignee"),
            null,
            true);

    // when
    final var request =
        new BrokerCreateBatchOperationRequest().setAuthorizationCheck(authorization);

    // then
    final var check =
        MsgPackConverter.convertToObject(
            request.requestDto.getAuthorizationCheckBuffer(),
            BatchOperationAuthorizationCheck.class);
    assertThat(check)
        .isEqualTo(
            new BatchOperationAuthorizationCheck(
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.UPDATE_PROCESS_INSTANCE,
                List.of("a", "b"),
                Set.of("assignee"),
                true));
  }
}
