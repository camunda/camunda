/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue.ClusterVariableSecretReferenceValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ClusterVariableSecretReferenceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldStoreSecretReferenceOnCreate() {
    // given
    final Record<ClusterVariableRecordValue> created =
        ENGINE
            .clusterVariables()
            .withName("var-create")
            .setGlobalScope()
            .withKind(ClusterVariableKind.SECRET_REFERENCE)
            .withValue(Map.of("auth", "camunda.secrets.token"))
            .create();

    // then
    assertThat(created.getValue().getSecretReferences())
        .extracting(
            ClusterVariableSecretReferenceValue::getStoreId,
            ClusterVariableSecretReferenceValue::getSecretReference,
            ClusterVariableSecretReferenceValue::getPath)
        .containsExactly(tuple("default", "token", "/auth"));
  }

  @Test
  public void shouldStoreSecretReferenceForRootScalarValue() {
    // given a value that is the string itself, not nested in an object
    final Record<ClusterVariableRecordValue> created =
        ENGINE
            .clusterVariables()
            .withName("var-root-scalar")
            .setGlobalScope()
            .withKind(ClusterVariableKind.SECRET_REFERENCE)
            .withValue("\"camunda.secrets.token\"")
            .create();

    // then the pointer of a root-level leaf is the empty string
    assertThat(created.getValue().getSecretReferences())
        .extracting(
            ClusterVariableSecretReferenceValue::getStoreId,
            ClusterVariableSecretReferenceValue::getSecretReference,
            ClusterVariableSecretReferenceValue::getPath)
        .containsExactly(tuple("default", "token", ""));
  }

  @Test
  public void shouldStoreSecretReferencesInNestedObjectsAndArrays() {
    // given
    final Record<ClusterVariableRecordValue> created =
        ENGINE
            .clusterVariables()
            .withName("var-nested")
            .setGlobalScope()
            .withKind(ClusterVariableKind.SECRET_REFERENCE)
            .withValue(
                Map.of(
                    "a", Map.of("b", "camunda.secrets.x"),
                    "list", List.of("camunda.secrets.y")))
            .create();

    // then
    assertThat(created.getValue().getSecretReferences())
        .extracting(
            ClusterVariableSecretReferenceValue::getStoreId,
            ClusterVariableSecretReferenceValue::getSecretReference,
            ClusterVariableSecretReferenceValue::getPath)
        .containsExactlyInAnyOrder(tuple("default", "x", "/a/b"), tuple("default", "y", "/list/0"));
  }

  @Test
  public void shouldDedupeRepeatedReferenceWithinSameLeaf() {
    // given a leaf with two distinct references, one of them repeated
    final Record<ClusterVariableRecordValue> created =
        ENGINE
            .clusterVariables()
            .withName("var-multi-ref-leaf")
            .setGlobalScope()
            .withKind(ClusterVariableKind.SECRET_REFERENCE)
            .withValue("\"camunda.secrets.a and camunda.secrets.b and camunda.secrets.a\"")
            .create();

    // then the repeated occurrence of "a" collapses into a single entry
    assertThat(created.getValue().getSecretReferences())
        .extracting(
            ClusterVariableSecretReferenceValue::getStoreId,
            ClusterVariableSecretReferenceValue::getSecretReference,
            ClusterVariableSecretReferenceValue::getPath)
        .containsExactly(tuple("default", "a", ""), tuple("default", "b", ""));
  }

  @Test
  public void shouldNotScanJsonKindValue() {
    // given a JSON-kind value that happens to contain a secret-reference-shaped string
    final Record<ClusterVariableRecordValue> created =
        ENGINE
            .clusterVariables()
            .withName("var-json-kind")
            .setGlobalScope()
            .withValue("\"camunda.secrets.token\"")
            .create();

    // then
    assertThat(created.getValue().getKind()).isEqualTo(ClusterVariableKind.JSON);
    assertThat(created.getValue().getSecretReferences()).isEmpty();
  }

  @Test
  public void shouldReplaceStoredSecretReferencesOnUpdate() {
    // given a SECRET_REFERENCE variable with a reference
    ENGINE
        .clusterVariables()
        .withName("var-update-replace")
        .setGlobalScope()
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue(Map.of("auth", "camunda.secrets.token"))
        .create();

    // when updated to a value without any secret reference
    final Record<ClusterVariableRecordValue> updated =
        ENGINE
            .clusterVariables()
            .withName("var-update-replace")
            .setGlobalScope()
            .withKind(ClusterVariableKind.SECRET_REFERENCE)
            .withValue(Map.of("plain", "value"))
            .update();

    // then the stored references end up empty, not appended to the old ones
    assertThat(updated.getValue().getSecretReferences()).isEmpty();
  }

  @Test
  public void shouldScanOnUpdateUsingStoredKindWhenCommandOmitsKind() {
    // given a SECRET_REFERENCE variable without any reference yet
    ENGINE
        .clusterVariables()
        .withName("var-update-omit-kind")
        .setGlobalScope()
        .withKind(ClusterVariableKind.SECRET_REFERENCE)
        .withValue("\"none\"")
        .create();

    // when updated without specifying a kind (the command's kind defaults to JSON)
    final Record<ClusterVariableRecordValue> updated =
        ENGINE
            .clusterVariables()
            .withName("var-update-omit-kind")
            .setGlobalScope()
            .withValue(Map.of("auth", "camunda.secrets.token"))
            .update();

    // then the update still scans, since the stored kind (SECRET_REFERENCE) is used, not the
    // command's (omitted, defaulting to JSON)
    assertThat(updated.getValue().getSecretReferences())
        .extracting(
            ClusterVariableSecretReferenceValue::getStoreId,
            ClusterVariableSecretReferenceValue::getSecretReference,
            ClusterVariableSecretReferenceValue::getPath)
        .containsExactly(tuple("default", "token", "/auth"));
  }
}
