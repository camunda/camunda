/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Resource;
import io.camunda.qa.util.auth.TenantDefinition;
import io.camunda.qa.util.auth.TestTenant;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.InitializationConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
class ResourceSearchIT {

  private static CamundaClient camundaClient;

  private static final List<Resource> RESOURCES = new ArrayList<>();

  @MultiDbTestApplication
  private static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication().withBasicAuth().withMultiTenancyEnabled();

  @TenantDefinition
  private static final TestTenant TENANT_B =
      new TestTenant("tenantB").addUsers(InitializationConfiguration.DEFAULT_USER_USERNAME);

  @BeforeAll
  static void beforeAll() {
    RESOURCES.add(
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("rpa/test-rpa.rpa")
            .execute()
            .getResource()
            .getFirst());
    RESOURCES.add(
        camundaClient
            .newDeployResourceCommand()
            .addResourceBytes("## MD".getBytes(StandardCharsets.UTF_8), "one.md")
            .execute()
            .getResource()
            .getFirst());
    RESOURCES.add(
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("rpa/test-rpa-2.rpa")
            .tenantId("tenantB")
            .execute()
            .getResource()
            .getFirst());
    RESOURCES.add(
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("rpa/test-rpa-21.rpa")
            .tenantId("tenantB")
            .execute()
            .getResource()
            .getFirst());

    Awaitility.await("resources should be available in secondary storage")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var res1 =
                  camundaClient
                      .newResourceGetRequest(RESOURCES.getFirst().getResourceKey())
                      .send()
                      .join();
              assertThat(res1).isNotNull();
              final var res2 =
                  camundaClient
                      .newResourceGetRequest(RESOURCES.getLast().getResourceKey())
                      .send()
                      .join();
              assertThat(res2).isNotNull();
            });
  }

  @Test
  void shouldRetrieveAllResources() {
    // when
    final var result = camundaClient.newResourceSearchRequest().send().join();

    // then
    assertThat(result.items()).hasSize(RESOURCES.size());
  }

  @Test
  void shouldSearchByResourceKey() {
    // when
    final Resource first = RESOURCES.getFirst();
    final var result =
        camundaClient
            .newResourceSearchRequest()
            .filter(f -> f.resourceKey(first.getResourceKey()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getResourceKey()).isEqualTo(first.getResourceKey());
    assertThat(result.items().getFirst().getResourceId()).isEqualTo(first.getResourceId());
    assertThat(result.items().getFirst().getResourceName()).isEqualTo(first.getResourceName());
    assertThat(result.items().getFirst().getVersion()).isEqualTo(first.getVersion());
    assertThat(result.items().getFirst().getTenantId()).isEqualTo(first.getTenantId());
  }

  @Test
  void shouldSearchByResourceId() {
    // when
    final var result =
        camundaClient
            .newResourceSearchRequest()
            .filter(f -> f.resourceId(RESOURCES.getFirst().getResourceId()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getResourceId())
        .isEqualTo(RESOURCES.getFirst().getResourceId());
  }

  @Test
  void shouldSearchByResourceName() {
    // when
    final var result =
        camundaClient
            .newResourceSearchRequest()
            .filter(f -> f.resourceName(RESOURCES.getLast().getResourceName()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getResourceName())
        .isEqualTo(RESOURCES.getLast().getResourceName());
  }

  @Test
  void shouldSearchByTenantId() {
    // when
    final var result =
        camundaClient.newResourceSearchRequest().filter(f -> f.tenantId("tenantB")).send().join();

    // then
    assertThat(result.items()).hasSize(2);
    result.items().forEach(r -> assertThat(r.getTenantId()).isEqualTo("tenantB"));
  }

  @Test
  void shouldSearchByVersion() {
    // when
    final var result =
        camundaClient
            .newResourceSearchRequest()
            .filter(f -> f.version(RESOURCES.getLast().getVersion()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getVersion()).isEqualTo(2);
  }

  @Test
  void shouldSearchByVersionGt() {
    // when
    final var result =
        camundaClient.newResourceSearchRequest().filter(f -> f.version(v -> v.gt(1))).send().join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getVersion()).isEqualTo(2);
  }

  @Test
  void shouldSortByResourceKey() {
    // when
    final var resultAsc =
        camundaClient.newResourceSearchRequest().sort(s -> s.resourceKey().asc()).send().join();
    final var resultDesc =
        camundaClient.newResourceSearchRequest().sort(s -> s.resourceKey().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Resource::getResourceKey)
        .containsExactlyElementsOf(
            RESOURCES.stream()
                .map(Resource::getResourceKey)
                .sorted(Comparator.naturalOrder())
                .toList());
    assertThat(resultDesc.items())
        .extracting(Resource::getResourceKey)
        .containsExactlyElementsOf(
            RESOURCES.stream()
                .map(Resource::getResourceKey)
                .sorted(Comparator.reverseOrder())
                .toList());
  }

  @Test
  void shouldSortByResourceName() {
    // when
    final var resultAsc =
        camundaClient.newResourceSearchRequest().sort(s -> s.resourceName().asc()).send().join();
    final var resultDesc =
        camundaClient.newResourceSearchRequest().sort(s -> s.resourceName().desc()).send().join();

    // then
    assertThat(resultAsc.items())
        .extracting(Resource::getResourceName)
        .containsExactlyElementsOf(
            RESOURCES.stream()
                .map(Resource::getResourceName)
                .sorted(Comparator.naturalOrder())
                .toList());
    assertThat(resultDesc.items())
        .extracting(Resource::getResourceName)
        .containsExactlyElementsOf(
            RESOURCES.stream()
                .map(Resource::getResourceName)
                .sorted(Comparator.reverseOrder())
                .toList());
  }

  @Test
  void shouldPaginateResults() {
    // when
    final var firstPage =
        camundaClient
            .newResourceSearchRequest()
            .sort(s -> s.resourceKey().asc())
            .page(p -> p.limit(1))
            .send()
            .join();

    // then
    assertThat(firstPage.items()).hasSize(1);
    final long firstKey = firstPage.items().getFirst().getResourceKey();

    // when - get next page
    final var secondPage =
        camundaClient
            .newResourceSearchRequest()
            .sort(s -> s.resourceKey().asc())
            .page(p -> p.after(firstPage.page().endCursor()))
            .send()
            .join();

    // then
    assertThat(secondPage.items()).hasSize(RESOURCES.size() - 1);
    assertThat(secondPage.items().getFirst().getResourceKey()).isGreaterThan(firstKey);
  }

  @Test
  void shouldReturnEmptyResultForUnknownResourceId() {
    // when
    final var result =
        camundaClient
            .newResourceSearchRequest()
            .filter(f -> f.resourceId("non-existent-resource-id"))
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }
}
