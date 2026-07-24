/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class ClusterVariableMetadataIT {

  private static CamundaClient camundaClient;

  // Unique marker shared by all search fixtures so filters only match this test's data.
  private static final String GROUP = UUID.randomUUID().toString();
  private static final String SCHEMA_REF = "io.camunda.connector.slack:" + UUID.randomUUID();

  // Search fixtures
  private static String credVar1;
  private static String credVar2;
  private static String configVar;

  @BeforeAll
  static void setupSearchFixtures() {
    credVar1 = "credVar1_" + UUID.randomUUID();
    credVar2 = "credVar2_" + UUID.randomUUID();
    configVar = "configVar_" + UUID.randomUUID();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(credVar1, "v1_" + credVar1)
        .metadata(metadata("kind", "CREDENTIAL", "schemaRef", SCHEMA_REF, "schemaVersion", 2))
        .send()
        .join();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(credVar2, "v2_" + credVar2)
        .metadata(metadata("kind", "CREDENTIAL", "schemaRef", SCHEMA_REF, "schemaVersion", 3))
        .send()
        .join();

    // Same group, but a different kind and no schemaVersion key.
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(configVar, "v_" + configVar)
        .metadata(metadata("kind", "CONFIG"))
        .send()
        .join();

    TestHelper.waitForClusterVariablesToBeIndexed(
        camundaClient,
        Map.of(
            credVar1, "v1_" + credVar1,
            credVar2, "v2_" + credVar2,
            configVar, "v_" + configVar));
  }

  // Builds a metadata bag that always carries the GROUP marker plus the given key/value pairs.
  private static Map<String, Object> metadata(final Object... keyValues) {
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("group", GROUP);
    for (int i = 0; i < keyValues.length; i += 2) {
      metadata.put((String) keyValues[i], keyValues[i + 1]);
    }
    return metadata;
  }

  // ============ CRUD ============

  @Test
  void shouldReturnMetadataOnGet() {
    // given
    final var name = "crudCreate_" + UUID.randomUUID();
    final var value = "value_" + UUID.randomUUID();
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(name, value)
        .metadata(metadata("kind", "CREDENTIAL", "schemaVersion", 2))
        .send()
        .join();
    TestHelper.waitForClusterVariableToBeIndexed(camundaClient, name, value);

    // when
    final var response =
        camundaClient.newGloballyScopedClusterVariableGetRequest().withName(name).send().join();

    // then
    assertThat(response.getMetadata()).containsEntry("kind", "CREDENTIAL");
    assertThat(((Number) response.getMetadata().get("schemaVersion")).intValue()).isEqualTo(2);
  }

  @Test
  void shouldReflectMetadataUpdate() {
    // given
    final var name = "crudUpdate_" + UUID.randomUUID();
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(name, "initial")
        .metadata(metadata("kind", "CREDENTIAL", "schemaVersion", 1))
        .send()
        .join();

    // when
    final var updatedValue = "updated_" + UUID.randomUUID();
    camundaClient
        .newGloballyScopedClusterVariableUpdateRequest()
        .update(name, updatedValue)
        .metadata(metadata("kind", "CREDENTIAL", "schemaVersion", 2))
        .send()
        .join();
    TestHelper.waitForClusterVariableToBeIndexed(camundaClient, name, updatedValue);

    // then
    final var response =
        camundaClient.newGloballyScopedClusterVariableGetRequest().withName(name).send().join();
    assertThat(((Number) response.getMetadata().get("schemaVersion")).intValue()).isEqualTo(2);
  }

  @Test
  void shouldCreateVariableWithoutMetadata() {
    // given
    final var name = "crudNoMetadata_" + UUID.randomUUID();
    final var value = "value_" + UUID.randomUUID();
    camundaClient.newGloballyScopedClusterVariableCreateRequest().create(name, value).send().join();
    TestHelper.waitForClusterVariableToBeIndexed(camundaClient, name, value);

    // when
    final var response =
        camundaClient.newGloballyScopedClusterVariableGetRequest().withName(name).send().join();

    // then
    assertThat(response.getValue()).isEqualTo("\"%s\"".formatted(value));
    assertThat(response.getMetadata()).isNullOrEmpty();
  }

  @Test
  void shouldRemoveMetadataOnDelete() {
    // given
    final var name = "crudDelete_" + UUID.randomUUID();
    final var value = "value_" + UUID.randomUUID();
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(name, value)
        .metadata(metadata("kind", "CREDENTIAL"))
        .send()
        .join();
    TestHelper.waitForClusterVariableToBeIndexed(camundaClient, name, value);

    // when
    camundaClient.newGloballyScopedClusterVariableDeleteRequest().delete(name).send().join();

    // then - the variable and its metadata are fully removed
    Awaitility.await("cluster variable is removed")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThatThrownBy(
                        () ->
                            camundaClient
                                .newGloballyScopedClusterVariableGetRequest()
                                .withName(name)
                                .send()
                                .join())
                    .isInstanceOf(ProblemException.class)
                    .hasMessageContaining("Failed with code 404: 'Not Found'"));
  }

  // ============ Search / filtering ============

  @Test
  void shouldFilterByExactMetadataMatch() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.metadata(Map.of("group", (Object) GROUP, "kind", "CREDENTIAL")))
            .send()
            .join();

    // then - only the two CREDENTIAL variables match, not the CONFIG one
    assertThat(response.items()).extracting(v -> v.getName()).contains(credVar1, credVar2);
    assertThat(response.items()).extracting(v -> v.getName()).doesNotContain(configVar);
  }

  @Test
  void shouldFilterByNumericRangeWithFloorSemantics() {
    // when - schemaVersion >= 3 (credVar1 has 2, credVar2 has 3)
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(GROUP))
                        .metadata("schemaVersion", m -> m.gte(3.0)))
            .send()
            .join();

    // then - the floor is inclusive: 3 matches, 2 does not
    assertThat(response.items()).extracting(v -> v.getName()).contains(credVar2);
    assertThat(response.items()).extracting(v -> v.getName()).doesNotContain(credVar1, configVar);
  }

  @Test
  void shouldFilterByMetadataKeyExistence() {
    // when - only variables that have the schemaVersion key
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(GROUP))
                        .metadata("schemaVersion", m -> m.exists(true)))
            .send()
            .join();

    // then - configVar has no schemaVersion key and is excluded
    assertThat(response.items()).extracting(v -> v.getName()).contains(credVar1, credVar2);
    assertThat(response.items()).extracting(v -> v.getName()).doesNotContain(configVar);
  }

  @Test
  void shouldCombineMetadataFilterWithScopeAndName() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.scope(ClusterVariableScope.GLOBAL)
                        .name(credVar1)
                        .metadata("group", m -> m.eq(GROUP)))
            .send()
            .join();

    // then - filters are intersected: only credVar1 remains
    assertThat(response.items()).extracting(v -> v.getName()).containsExactly(credVar1);
  }

  // ============ Validation ============

  @Test
  void shouldRejectBooleanMetadataValue() {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create("boolMeta_" + UUID.randomUUID(), "value")
                    .metadata(Map.of("enabled", (Object) true))
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("must be a string or a number");
  }

  @Test
  void shouldRejectArrayMetadataValue() {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create("arrayMeta_" + UUID.randomUUID(), "value")
                    .metadata(Map.of("roles", (Object) List.of("admin", "user")))
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("must be a string or a number");
  }

  @Test
  void shouldRejectObjectMetadataValue() {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create("objectMeta_" + UUID.randomUUID(), "value")
                    .metadata(Map.of("nested", (Object) Map.of("a", "b")))
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("must be a string or a number");
  }

  @Test
  void shouldRejectMetadataExceedingSizeLimit() {
    final Map<String, Object> oversized = Map.of("big", (Object) "x".repeat(900_000));
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create("oversizedMeta_" + UUID.randomUUID(), "value")
                    .metadata(oversized)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("exceeds the maximum serialized size");
  }

  // ============ Runtime isolation ============

  @Test
  void shouldNotLeakMetadataIntoRuntimeValue() {
    // given - a variable with both a value and metadata
    final var name = "runtimeVar_" + UUID.randomUUID().toString().replace("-", "");
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(name, Map.of("user", "alice"))
        .metadata(metadata("kind", "CREDENTIAL", "schemaVersion", 2))
        .send()
        .join();

    // when / then - the FEEL-accessible value exposes only the value contents
    final var valueResult =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + name + ".user")
            .send()
            .join();
    assertThat(valueResult.getResult()).isEqualTo("alice");

    // metadata keys are not reachable through the runtime value
    final var metadataResult =
        camundaClient
            .newEvaluateExpressionCommand()
            .expression("=camunda.vars.cluster." + name + ".kind")
            .send()
            .join();
    assertThat(metadataResult.getResult()).isNull();
  }
}
