/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.aggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.authorization.result.AuthorizationRejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class RejectionAggregatorTest {

  @Test
  void shouldPrioritizePermissionRejectionsOverTenantRejections() {
    // given
    final var permissionRejection =
        new AuthorizationRejection.Permission(
            new Rejection(RejectionType.FORBIDDEN, "permission denied"));
    final var tenantRejection =
        new AuthorizationRejection.Tenant(
            new Rejection(RejectionType.NOT_FOUND, "tenant not found"));

    // when
    final var result = RejectionAggregator.aggregate(List.of(tenantRejection, permissionRejection));

    // then
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason()).isEqualTo("permission denied");
  }

  @Test
  void shouldAggregateMultiplePermissionRejections() {
    // given
    final var permissionRejection1 =
        new AuthorizationRejection.Permission(
            new Rejection(RejectionType.FORBIDDEN, "permission denied 1"));
    final var permissionRejection2 =
        new AuthorizationRejection.Permission(
            new Rejection(RejectionType.FORBIDDEN, "permission denied 2"));

    // when
    final var result =
        RejectionAggregator.aggregate(List.of(permissionRejection1, permissionRejection2));

    // then
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason()).isEqualTo("permission denied 1; permission denied 2");
  }

  @Test
  void shouldDeduplicateIdenticalPermissionRejectionReasons() {
    // given
    final var permissionRejection1 =
        new AuthorizationRejection.Permission(
            new Rejection(RejectionType.FORBIDDEN, "permission denied"));
    final var permissionRejection2 =
        new AuthorizationRejection.Permission(
            new Rejection(RejectionType.FORBIDDEN, "permission denied"));

    // when
    final var result =
        RejectionAggregator.aggregate(List.of(permissionRejection1, permissionRejection2));

    // then
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason()).isEqualTo("permission denied");
  }

  @Test
  void shouldReturnTenantRejectionWhenNoPermissionRejections() {
    // given
    final var tenantRejection =
        new AuthorizationRejection.Tenant(
            new Rejection(RejectionType.NOT_FOUND, "tenant not found"));

    // when
    final var result = RejectionAggregator.aggregate(List.of(tenantRejection));

    // then
    assertThat(result.type()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(result.reason()).isEqualTo("tenant not found");
  }

  @Test
  void shouldAggregateMultipleTenantRejections() {
    // given
    final var tenantRejection1 =
        new AuthorizationRejection.Tenant(
            new Rejection(RejectionType.NOT_FOUND, "tenant A not found"));
    final var tenantRejection2 =
        new AuthorizationRejection.Tenant(
            new Rejection(RejectionType.NOT_FOUND, "tenant B not found"));

    // when
    final var result = RejectionAggregator.aggregate(List.of(tenantRejection1, tenantRejection2));

    // then
    assertThat(result.type()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(result.reason()).isEqualTo("tenant A not found; tenant B not found");
  }

  @Test
  void shouldDeduplicateIdenticalTenantRejectionReasons() {
    // given
    final var tenantRejection1 =
        new AuthorizationRejection.Tenant(
            new Rejection(RejectionType.NOT_FOUND, "tenant not found"));
    final var tenantRejection2 =
        new AuthorizationRejection.Tenant(
            new Rejection(RejectionType.NOT_FOUND, "tenant not found"));

    // when
    final var result = RejectionAggregator.aggregate(List.of(tenantRejection1, tenantRejection2));

    // then
    assertThat(result.type()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(result.reason()).isEqualTo("tenant not found");
  }

  @Test
  void aggregateShouldThrowExceptionForEmptyList() {
    // when / then
    assertThatThrownBy(() -> RejectionAggregator.aggregate(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot aggregate empty list of authorization rejections");
  }

  @Test
  void aggregateCompositeShouldHandleSingleRejection() {
    // given
    final var rejection = new Rejection(RejectionType.FORBIDDEN, "permission denied");

    // when
    final var result = RejectionAggregator.aggregateComposite(List.of(rejection));

    // then
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason()).isEqualTo("permission denied");
  }

  @Test
  void aggregateCompositeShouldCombineMultipleRejectionMessages() {
    // given
    final var rejection1 =
        new Rejection(
            RejectionType.FORBIDDEN,
            "no 'READ_USER_TASK' permission on resource 'PROCESS_DEFINITION'");
    final var rejection2 =
        new Rejection(RejectionType.FORBIDDEN, "no 'READ' permission on resource 'USER_TASK'");

    // when
    final var result = RejectionAggregator.aggregateComposite(List.of(rejection1, rejection2));

    // then
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason())
        .isEqualTo(
            "no 'READ_USER_TASK' permission on resource 'PROCESS_DEFINITION'; and no 'READ' permission on resource 'USER_TASK'");
  }

  @Test
  void aggregateCompositeShouldDeduplicateIdenticalReasons() {
    // given
    final var rejection1 = new Rejection(RejectionType.FORBIDDEN, "permission denied");
    final var rejection2 = new Rejection(RejectionType.FORBIDDEN, "permission denied");

    // when
    final var result = RejectionAggregator.aggregateComposite(List.of(rejection1, rejection2));

    // then
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason()).isEqualTo("permission denied");
  }

  @Test
  void aggregateCompositeShouldUseForbiddenTypeWhenAnyIsForbidden() {
    // given
    final var rejection1 = new Rejection(RejectionType.NOT_FOUND, "resource not found");
    final var rejection2 = new Rejection(RejectionType.FORBIDDEN, "permission denied");

    // when
    final var result = RejectionAggregator.aggregateComposite(List.of(rejection1, rejection2));

    // then
    assertThat(result.type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.reason()).isEqualTo("resource not found; and permission denied");
  }

  @Test
  void aggregateCompositeShouldUseFirstRejectionTypeWhenNoForbidden() {
    // given
    final var rejection1 = new Rejection(RejectionType.NOT_FOUND, "resource A not found");
    final var rejection2 = new Rejection(RejectionType.NOT_FOUND, "resource B not found");

    // when
    final var result = RejectionAggregator.aggregateComposite(List.of(rejection1, rejection2));

    // then
    assertThat(result.type()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(result.reason()).isEqualTo("resource A not found; and resource B not found");
  }

  @Test
  void aggregateCompositeShouldThrowExceptionForEmptyList() {
    // when
    assertThatThrownBy(() -> RejectionAggregator.aggregateComposite(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot aggregate empty list of rejections");
  }
}
