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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.client.api.search.response.ClusterVariable;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.auth.TenantDefinition;
import io.camunda.qa.util.auth.TestTenant;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
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

  // Unique marker shared by the search fixtures so filters only match those four variables.
  private static final String SEARCH_GROUP = UUID.randomUUID().toString();

  // Separate marker for variables created by the CRUD and runtime tests. Those variables stay
  // indexed for the rest of the run, so they must never carry the marker the search tests filter
  // on, otherwise they leak into exact-result assertions depending on indexing timing.
  private static final String OTHER_GROUP = UUID.randomUUID().toString();
  private static final String SCHEMA_REF = "io.camunda.connector.slack:" + UUID.randomUUID();
  private static final String OTHER_SCHEMA_REF = "io.camunda.connector.github:" + UUID.randomUUID();
  private static final String TENANT_ID = "metadata_tenant";

  @TenantDefinition
  private static final TestTenant TENANT =
      new TestTenant(TENANT_ID).setName("Cluster variable metadata tenant");

  // Search fixtures
  private static String credVar1;
  private static String credVar2;
  private static String configVar;
  private static String tenantCredVar;

  @BeforeAll
  static void setupSearchFixtures() {
    credVar1 = "credVar1_" + UUID.randomUUID();
    credVar2 = "credVar2_" + UUID.randomUUID();
    configVar = "configVar_" + UUID.randomUUID();
    tenantCredVar = "tenantCredVar_" + UUID.randomUUID();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(credVar1, "v1_" + credVar1)
        .metadata(
            searchMetadata("testValue", "CREDENTIAL", "schemaRef", SCHEMA_REF, "schemaVersion", 2))
        .send()
        .join();

    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(credVar2, "v2_" + credVar2)
        .metadata(
            searchMetadata("testValue", "CREDENTIAL", "schemaRef", SCHEMA_REF, "schemaVersion", 3))
        .send()
        .join();

    // Same group, but a different kind and no schemaVersion key.
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(configVar, "v_" + configVar)
        .metadata(searchMetadata("testValue", "CONFIG"))
        .send()
        .join();

    // Same group and kind, but tenant-scoped and with a different schemaRef.
    camundaClient
        .newTenantScopedClusterVariableCreateRequest(TENANT_ID)
        .create(tenantCredVar, "vt_" + tenantCredVar)
        .metadata(
            searchMetadata(
                "testValue", "CREDENTIAL", "schemaRef", OTHER_SCHEMA_REF, "schemaVersion", 4))
        .send()
        .join();

    TestHelper.waitForClusterVariablesToBeIndexed(
        camundaClient,
        Map.of(
            credVar1, "v1_" + credVar1,
            credVar2, "v2_" + credVar2,
            configVar, "v_" + configVar));
    TestHelper.waitForClusterVariableToBeIndexed(
        camundaClient, tenantCredVar, TENANT_ID, "vt_" + tenantCredVar);
  }

  // Metadata bag for the search fixtures: carries SEARCH_GROUP plus the given key/value pairs.
  private static Map<String, Object> searchMetadata(final Object... keyValues) {
    return metadata(SEARCH_GROUP, keyValues);
  }

  // Metadata bag for everything outside the search fixtures, marked so it cannot match a search.
  private static Map<String, Object> otherMetadata(final Object... keyValues) {
    return metadata(OTHER_GROUP, keyValues);
  }

  private static Map<String, Object> metadata(final String group, final Object... keyValues) {
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("group", group);
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
    final var expectedMetadata = otherMetadata("testValue", "CREDENTIAL", "schemaVersion", 2);
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(name, value)
        .metadata(expectedMetadata)
        .send()
        .join();
    TestHelper.waitForClusterVariableToBeIndexed(camundaClient, name, value);

    // when
    final var response =
        camundaClient.newGloballyScopedClusterVariableGetRequest().withName(name).send().join();

    // then
    assertThat(response.getMetadata())
        .hasSize(expectedMetadata.size())
        .containsEntry("group", OTHER_GROUP)
        .containsEntry("testValue", "CREDENTIAL");
    assertThat(((Number) response.getMetadata().get("schemaVersion")).intValue()).isEqualTo(2);
  }

  @Test
  void shouldReplaceMetadataOnUpdate() {
    // given
    final var name = "crudUpdate_" + UUID.randomUUID();
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(name, "initial")
        .metadata(otherMetadata("obsolete", "remove-me", "schemaVersion", 1))
        .send()
        .join();

    // when
    final var updatedValue = "updated_" + UUID.randomUUID();
    camundaClient
        .newGloballyScopedClusterVariableUpdateRequest()
        .update(name, updatedValue)
        .metadata(otherMetadata("schemaVersion", 2))
        .send()
        .join();
    TestHelper.waitForClusterVariableToBeIndexed(camundaClient, name, updatedValue);

    // then
    final var response =
        camundaClient.newGloballyScopedClusterVariableGetRequest().withName(name).send().join();
    assertThat(response.getMetadata()).doesNotContainKey("obsolete");
    assertThat(response.getMetadata()).containsEntry("group", OTHER_GROUP);
    assertThat(((Number) response.getMetadata().get("schemaVersion")).intValue()).isEqualTo(2);
  }

  @Test
  void shouldClearMetadataWhenUpdateOmitsMetadata() {
    // given
    final var name = "crudClear_" + UUID.randomUUID();
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(name, "initial")
        .metadata(otherMetadata())
        .send()
        .join();

    // when
    final var updatedValue = "updated_" + UUID.randomUUID();
    camundaClient
        .newGloballyScopedClusterVariableUpdateRequest()
        .update(name, updatedValue)
        .send()
        .join();
    TestHelper.waitForClusterVariableToBeIndexed(camundaClient, name, updatedValue);

    // then
    final var response =
        camundaClient.newGloballyScopedClusterVariableGetRequest().withName(name).send().join();
    assertThat(response.getMetadata()).isEmpty();
    Awaitility.await("cluster variable metadata removal is indexed")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newClusterVariableSearchRequest()
                            .filter(f -> f.name(name).metadata("group", m -> m.eq(OTHER_GROUP)))
                            .send()
                            .join()
                            .items())
                    .isEmpty());
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
        .metadata(otherMetadata("testValue", "CREDENTIAL"))
        .send()
        .join();
    TestHelper.waitForClusterVariableToBeIndexed(camundaClient, name, value);

    // when
    camundaClient.newGloballyScopedClusterVariableDeleteRequest().delete(name).send().join();

    // then - the variable and its metadata are fully removed
    Awaitility.await("cluster variable is removed")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
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
            .filter(f -> f.metadata(Map.of("group", SEARCH_GROUP, "testValue", "CREDENTIAL")))
            .send()
            .join();

    // then - only the two CREDENTIAL variables match, not the CONFIG one
    assertThat(response.items()).extracting(ClusterVariable::getName).contains(credVar1, credVar2);
    assertThat(response.items()).extracting(ClusterVariable::getName).doesNotContain(configVar);
    assertThat(response.items())
        .filteredOn(item -> item.getName().equals(credVar1))
        .singleElement()
        .satisfies(item -> assertThat(item.getMetadata()).containsEntry("testValue", "CREDENTIAL"));
  }

  @Test
  void shouldFilterByNumericMetadataEquality() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(SEARCH_GROUP))
                        .metadata("schemaVersion", m -> m.eq(2)))
            .send()
            .join();

    // then
    assertThat(response.items()).extracting(ClusterVariable::getName).containsExactly(credVar1);
  }

  @Test
  void shouldFilterByNumericRangeWithFloorSemantics() {
    // when - schemaVersion >= 3 (credVar1 has 2, credVar2 has 3)
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(SEARCH_GROUP))
                        .metadata("schemaVersion", m -> m.gte(3.0)))
            .send()
            .join();

    // then - the floor is inclusive: 3 matches, 2 does not
    assertThat(response.items()).extracting(ClusterVariable::getName).contains(credVar2);
    assertThat(response.items())
        .extracting(ClusterVariable::getName)
        .doesNotContain(credVar1, configVar);
  }

  @Test
  void shouldFilterByMetadataKeyExistence() {
    // when - only variables that have the schemaVersion key
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(SEARCH_GROUP))
                        .metadata("schemaVersion", m -> m.exists(true)))
            .send()
            .join();

    // then - configVar has no schemaVersion key and is excluded
    assertThat(response.items()).extracting(ClusterVariable::getName).contains(credVar1, credVar2);
    assertThat(response.items()).extracting(ClusterVariable::getName).doesNotContain(configVar);
  }

  @Test
  void shouldFilterByMetadataKeyNonExistence() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(SEARCH_GROUP))
                        .metadata("schemaVersion", m -> m.exists(false)))
            .send()
            .join();

    // then - only configVar is in the group without schemaVersion
    assertThat(response.items()).extracting(ClusterVariable::getName).containsExactly(configVar);
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
                        .metadata("group", m -> m.eq(SEARCH_GROUP)))
            .send()
            .join();

    // then - filters are intersected: only credVar1 remains
    assertThat(response.items()).extracting(ClusterVariable::getName).containsExactly(credVar1);
  }

  @Test
  void shouldFindGlobalAndTenantScopedVariablesByMetadata() {
    // when - a metadata-only filter, without narrowing by scope or tenant
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.metadata(Map.of("group", SEARCH_GROUP, "testValue", "CREDENTIAL")))
            .send()
            .join();

    // then - both the globally scoped and the tenant-scoped variables match
    assertThat(response.items())
        .extracting(ClusterVariable::getName)
        .contains(credVar1, credVar2, tenantCredVar);
    assertThat(response.items())
        .filteredOn(item -> item.getName().equals(tenantCredVar))
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getScope()).isEqualTo(ClusterVariableScope.TENANT);
              assertThat(item.getTenantId()).isEqualTo(TENANT_ID);
              assertThat(item.getMetadata()).containsEntry("testValue", "CREDENTIAL");
            });
    assertThat(response.items())
        .filteredOn(item -> item.getName().equals(credVar1))
        .singleElement()
        .satisfies(item -> assertThat(item.getScope()).isEqualTo(ClusterVariableScope.GLOBAL));
  }

  @Test
  void shouldCombineMetadataFilterWithTenantId() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(f -> f.tenantId(TENANT_ID).metadata("group", m -> m.eq(SEARCH_GROUP)))
            .send()
            .join();

    // then - only the tenant-scoped variable of this group remains
    assertThat(response.items())
        .extracting(ClusterVariable::getName)
        .containsExactly(tenantCredVar);
  }

  @Test
  void shouldFilterByMetadataPattern() {
    // when - schemaRef matches the slack connector prefix
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(SEARCH_GROUP))
                        .metadata("schemaRef", m -> m.like("*connector.slack*")))
            .send()
            .join();

    // then - the github schemaRef and the variable without a schemaRef are excluded
    assertThat(response.items())
        .extracting(ClusterVariable::getName)
        .containsExactlyInAnyOrder(credVar1, credVar2);
  }

  @Test
  void shouldFilterByNumericRangeGt() {
    // when - schemaVersion > 3 (credVar1 has 2, credVar2 has 3, tenantCredVar has 4)
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(SEARCH_GROUP))
                        .metadata("schemaVersion", m -> m.gt(3.0)))
            .send()
            .join();

    // then - the floor is exclusive: 3 does not match
    assertThat(response.items())
        .extracting(ClusterVariable::getName)
        .containsExactly(tenantCredVar);
  }

  @Test
  void shouldFilterByNumericRangeLt() {
    // when - schemaVersion < 3
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(SEARCH_GROUP))
                        .metadata("schemaVersion", m -> m.lt(3.0)))
            .send()
            .join();

    // then - the ceiling is exclusive: 3 does not match
    assertThat(response.items()).extracting(ClusterVariable::getName).containsExactly(credVar1);
  }

  @Test
  void shouldFilterByNumericRangeLte() {
    // when - schemaVersion <= 3
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(SEARCH_GROUP))
                        .metadata("schemaVersion", m -> m.lte(3.0)))
            .send()
            .join();

    // then - the ceiling is inclusive: 3 matches
    assertThat(response.items())
        .extracting(ClusterVariable::getName)
        .containsExactlyInAnyOrder(credVar1, credVar2);
  }

  @Test
  void shouldFilterByMetadataInequality() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(SEARCH_GROUP))
                        .metadata("testValue", m -> m.neq("CREDENTIAL")))
            .send()
            .join();

    // then - only the CONFIG variable remains
    assertThat(response.items()).extracting(ClusterVariable::getName).containsExactly(configVar);
  }

  @Test
  void shouldFilterByMetadataValueList() {
    // when
    final var response =
        camundaClient
            .newClusterVariableSearchRequest()
            .filter(
                f ->
                    f.metadata("group", m -> m.eq(SEARCH_GROUP))
                        .metadata("testValue", m -> m.in("CONFIG", "UNKNOWN")))
            .send()
            .join();

    // then
    assertThat(response.items()).extracting(ClusterVariable::getName).containsExactly(configVar);
  }

  // ============ Validation ============

  @Test
  void shouldRejectBooleanMetadataValue() {
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create("boolMeta_" + UUID.randomUUID(), "value")
                    .metadata(Map.of("enabled", true))
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
                    .metadata(Map.of("roles", List.of("admin", "user")))
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
                    .metadata(Map.of("nested", Map.of("a", "b")))
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("must be a string or a number");
  }

  @Test
  void shouldRejectNullMetadataValueOnUpdate() {
    // given
    final var name = "nullMeta_" + UUID.randomUUID();
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(name, "initial")
        .send()
        .join();
    final Map<String, Object> metadata = new HashMap<>();
    metadata.put("testValue", null);

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableUpdateRequest()
                    .update(name, "updated")
                    .metadata(metadata)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("must be a string or a number");
  }

  @Test
  void shouldRejectMetadataWithTooManyEntries() {
    final Map<String, Object> metadata = new HashMap<>();
    for (int i = 0; i < 101; i++) {
      metadata.put("key-" + i, "value-" + i);
    }

    assertThatThrownBy(
            () ->
                camundaClient
                    .newGloballyScopedClusterVariableCreateRequest()
                    .create("tooManyMetadata_" + UUID.randomUUID(), "value")
                    .metadata(metadata)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("must not exceed 100 entries");
  }

  @Test
  void shouldRejectMetadataExceedingSizeLimit() {
    final Map<String, Object> oversized = Map.of("big", "x".repeat(900_000));
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
    final var metadata = otherMetadata("testValue", "CREDENTIAL", "schemaVersion", 2);
    camundaClient
        .newGloballyScopedClusterVariableCreateRequest()
        .create(name, Map.of("user", "alice"))
        .metadata(metadata)
        .send()
        .join();

    // when / then - the FEEL-accessible value exposes only the value contents
    for (final var namespace : List.of("camunda.vars.cluster", "camunda.vars.env")) {
      assertThat(evaluateExpression(namespace + "." + name + ".user"))
          .as("value contents must stay reachable via %s", namespace)
          .isEqualTo("alice");

      // every key the metadata bag actually carries must be unreachable
      for (final var metadataKey : metadata.keySet()) {
        assertThat(evaluateExpression(namespace + "." + name + "." + metadataKey))
            .as("metadata key '%s' must not be reachable via %s", metadataKey, namespace)
            .isNull();
      }
    }
  }

  private Object evaluateExpression(final String path) {
    return camundaClient
        .newEvaluateExpressionCommand()
        .expression("=" + path)
        .send()
        .join()
        .getResult();
  }
}
