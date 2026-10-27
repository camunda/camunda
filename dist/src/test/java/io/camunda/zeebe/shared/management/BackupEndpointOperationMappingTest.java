/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.invoke.convert.ConversionServiceParameterValueMapper;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.WebOperationRequestPredicate;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpointDiscoverer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Pins the request predicates the backup actuators map to. Spring Boot derives a write operation's
 * consumed media types from whether it declares any non-selector parameter, so giving one of the
 * parameterless {@code POST} overloads a {@code physicalTenant} parameter would hand it the same
 * predicate as its body-reading sibling — which fails endpoint discovery, and therefore startup,
 * long after every other unit test here has passed.
 *
 * <p>Both ids are discovered from one context, as a standalone broker or gateway exposes them
 * together.
 */
final class BackupEndpointOperationMappingTest {

  @Test
  void shouldMapEveryBackupOperationToItsOwnRequestPredicate() {
    // given both backup actuators, as a standalone broker or gateway registers them
    try (final var context = new AnnotationConfigApplicationContext()) {
      // BackupEndpointStandalone only exists on a standalone broker or gateway
      context.getEnvironment().setActiveProfiles("broker", "standalone");
      context.registerBean("backupEndpoint", BackupEndpoint.class, this::backupEndpoint);
      context.registerBean(
          "backupEndpointStandalone",
          BackupEndpointStandalone.class,
          () -> new BackupEndpointStandalone(backupEndpoint()));
      context.refresh();

      // when discovery runs, as it does on startup
      final var endpoints =
          new WebEndpointDiscoverer(
                  context,
                  new ConversionServiceParameterValueMapper(),
                  EndpointMediaTypes.DEFAULT,
                  null,
                  null,
                  List.of(),
                  List.of(),
                  List.of())
              .getEndpoints();

      // then it succeeds — no two operations share a predicate — and maps what the OpenAPI spec
      // describes, with the bodyless POST overloads consuming nothing so they stay distinct from
      // the ones that read a backupId and a physicalTenant out of the request
      assertThat(
              endpoints.stream()
                  .flatMap(endpoint -> endpoint.getOperations().stream())
                  .map(operation -> describe(operation.getRequestPredicate()))
                  .collect(Collectors.toUnmodifiableSet()))
          .containsExactlyInAnyOrderElementsOf(
              Stream.of("backupRuntime", "backups")
                  .flatMap(
                      id ->
                          Stream.of(
                              "POST %s consumes=json".formatted(id),
                              "POST %s consumes=none".formatted(id),
                              "POST %s/{*path} consumes=json".formatted(id),
                              "POST %s/{*path} consumes=none".formatted(id),
                              "GET %s consumes=none".formatted(id),
                              "GET %s/{prefixOrId} consumes=none".formatted(id),
                              "DELETE %s/{id} consumes=none".formatted(id)))
                  .toList());
    }
  }

  private BackupEndpoint backupEndpoint() {
    return new BackupEndpoint(mock(BackupApi.class), mock(BackupCfg.class));
  }

  private static String describe(final WebOperationRequestPredicate predicate) {
    return "%s %s consumes=%s"
        .formatted(
            predicate.getHttpMethod(),
            predicate.getPath(),
            predicate.getConsumes().isEmpty() ? "none" : "json");
  }
}
