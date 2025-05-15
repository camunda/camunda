/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.auth;

import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.zeebe.protocol.record.value.AuthorizationResourceType.BATCH_OPERATION;
import static io.camunda.zeebe.protocol.record.value.PermissionType.READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchOperationAuthorizationQueryTransformerTest {

  private final BatchOperationAuthorizationQueryTransformer transformer =
      new BatchOperationAuthorizationQueryTransformer();

  @Test
  void shouldReturnMatchAllForValidAuthorization() {
    // given
    final AuthorizationResourceType resourceType = BATCH_OPERATION;
    final PermissionType permissionType = READ;
    final List<String> resourceKeys = List.of("key1", "key2");

    // when
    final SearchQuery result =
        transformer.toSearchQuery(resourceType, permissionType, resourceKeys);

    // then
    assertThat(result).isEqualTo(matchAll());
  }

  @Test
  void shouldThrowExceptionForInvalidResourceType() {
    // given
    final AuthorizationResourceType resourceType = AuthorizationResourceType.PROCESS_DEFINITION;
    final PermissionType permissionType = READ;
    final List<String> resourceKeys = List.of("key1", "key2");

    // when & then
    assertThatThrownBy(() -> transformer.toSearchQuery(resourceType, permissionType, resourceKeys))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported authorizations with resource");
  }

  @Test
  void shouldThrowExceptionForInvalidPermissionType() {
    // given
    final AuthorizationResourceType resourceType = BATCH_OPERATION;
    final PermissionType permissionType = PermissionType.UPDATE;
    final List<String> resourceKeys = List.of("key1", "key2");

    // when & then
    assertThatThrownBy(() -> transformer.toSearchQuery(resourceType, permissionType, resourceKeys))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported authorizations with resource");
  }
}
