/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.usertask.DraftTaskVariableEntity;
import io.camunda.webapps.schema.entities.usertask.SnapshotTaskVariableEntity;
import org.junit.jupiter.api.Test;

class VariableResponseTest {

  @Test
  void shouldCreateFromVariableEntityWithTruncatedValue() {
    // given - a variable with truncated value (isPreview = true)
    final VariableEntity variableEntity = new VariableEntity();
    variableEntity.setId("var-1");
    variableEntity.setName("largeVariable");
    variableEntity.setValue("truncated..."); // preview value
    variableEntity.setFullValue("This is the full value that was truncated"); // full value
    variableEntity.setIsPreview(true);
    variableEntity.setTenantId("tenant-1");

    // when
    final VariableResponse response = VariableResponse.createFrom(variableEntity);

    // then
    assertThat(response.getId()).isEqualTo("var-1");
    assertThat(response.getName()).isEqualTo("largeVariable");
    assertThat(response.getValue()).isEqualTo("This is the full value that was truncated");
    assertThat(response.getTenantId()).isEqualTo("tenant-1");
  }

  @Test
  void shouldCreateFromVariableEntityWithNonTruncatedValue() {
    // given - a variable with non-truncated value (isPreview = false)
    final VariableEntity variableEntity = new VariableEntity();
    variableEntity.setId("var-2");
    variableEntity.setName("smallVariable");
    variableEntity.setValue("small value");
    variableEntity.setFullValue(null); // fullValue is null for non-truncated variables
    variableEntity.setIsPreview(false);
    variableEntity.setTenantId("tenant-1");

    // when
    final VariableResponse response = VariableResponse.createFrom(variableEntity);

    // then
    assertThat(response.getId()).isEqualTo("var-2");
    assertThat(response.getName()).isEqualTo("smallVariable");
    assertThat(response.getValue()).isEqualTo("small value");
    assertThat(response.getTenantId()).isEqualTo("tenant-1");
  }

  @Test
  void shouldCreateFromDraftTaskVariableEntity() {
    // given
    final DraftTaskVariableEntity draftVariable = new DraftTaskVariableEntity();
    draftVariable.setId("draft-1");
    draftVariable.setName("draftVariable");
    draftVariable.setFullValue("draft value");
    draftVariable.setTenantId("tenant-1");

    // when
    final VariableResponse response = VariableResponse.createFrom(draftVariable);

    // then
    assertThat(response.getId()).isEqualTo("draft-1");
    assertThat(response.getName()).isEqualTo("draftVariable");
    assertThat(response.getValue()).isNull();
    assertThat(response.getDraft()).isNotNull();
    assertThat(response.getDraft().getValue()).isEqualTo("draft value");
    assertThat(response.getTenantId()).isEqualTo("tenant-1");
  }

  @Test
  void shouldCreateFromSnapshotTaskVariableEntityWithTruncatedValue() {
    // given - a snapshot variable with truncated value (isPreview = true)
    final SnapshotTaskVariableEntity snapshotVariable = new SnapshotTaskVariableEntity();
    snapshotVariable.setId("snapshot-1");
    snapshotVariable.setName("largeSnapshotVariable");
    snapshotVariable.setValue("truncated..."); // preview value
    snapshotVariable.setFullValue("This is the full snapshot value that was truncated");
    snapshotVariable.setIsPreview(true);
    snapshotVariable.setTenantId("tenant-1");

    // when
    final VariableResponse response = VariableResponse.createFrom(snapshotVariable);

    // then
    assertThat(response.getId()).isEqualTo("snapshot-1");
    assertThat(response.getName()).isEqualTo("largeSnapshotVariable");
    assertThat(response.getValue()).isEqualTo("This is the full snapshot value that was truncated");
    assertThat(response.getTenantId()).isEqualTo("tenant-1");
  }

  @Test
  void shouldCreateFromSnapshotTaskVariableEntityWithNonTruncatedValue() {
    // given - a snapshot variable with non-truncated value (isPreview = false)
    final SnapshotTaskVariableEntity snapshotVariable = new SnapshotTaskVariableEntity();
    snapshotVariable.setId("snapshot-2");
    snapshotVariable.setName("smallSnapshotVariable");
    snapshotVariable.setValue("small snapshot value");
    snapshotVariable.setFullValue(null); // fullValue is null for non-truncated variables
    snapshotVariable.setIsPreview(false);
    snapshotVariable.setTenantId("tenant-1");

    // when
    final VariableResponse response = VariableResponse.createFrom(snapshotVariable);

    // then
    assertThat(response.getId()).isEqualTo("snapshot-2");
    assertThat(response.getName()).isEqualTo("smallSnapshotVariable");
    assertThat(response.getValue()).isEqualTo("small snapshot value");
    assertThat(response.getTenantId()).isEqualTo("tenant-1");
  }

  @Test
  void shouldFallbackToPreviewValueWhenFullValueIsNullForVariableEntity() {
    // given - a variable marked as preview but with no fullValue available
    final VariableEntity variableEntity = new VariableEntity();
    variableEntity.setId("var-preview-only");
    variableEntity.setName("largeVariablePreviewOnly");
    variableEntity.setValue("truncated...");
    variableEntity.setFullValue(null);
    variableEntity.setIsPreview(true);
    variableEntity.setTenantId("tenant-1");

    // when
    final VariableResponse response = VariableResponse.createFrom(variableEntity);

    // then
    assertThat(response.getId()).isEqualTo("var-preview-only");
    assertThat(response.getName()).isEqualTo("largeVariablePreviewOnly");
    assertThat(response.getValue()).isEqualTo("truncated...");
    assertThat(response.getTenantId()).isEqualTo("tenant-1");
  }

  @Test
  void shouldFallbackToPreviewValueWhenFullValueIsNullForSnapshotEntity() {
    // given - a snapshot variable marked as preview but with no fullValue available
    final SnapshotTaskVariableEntity snapshotVariable = new SnapshotTaskVariableEntity();
    snapshotVariable.setId("snapshot-preview-only");
    snapshotVariable.setName("largeSnapshotPreviewOnly");
    snapshotVariable.setValue("truncated...");
    snapshotVariable.setFullValue(null);
    snapshotVariable.setIsPreview(true);
    snapshotVariable.setTenantId("tenant-1");

    // when
    final VariableResponse response = VariableResponse.createFrom(snapshotVariable);

    // then
    assertThat(response.getId()).isEqualTo("snapshot-preview-only");
    assertThat(response.getName()).isEqualTo("largeSnapshotPreviewOnly");
    assertThat(response.getValue()).isEqualTo("truncated...");
    assertThat(response.getTenantId()).isEqualTo("tenant-1");
  }

  @Test
  void shouldAddDraftToVariableResponse() {
    // given
    final VariableResponse response = new VariableResponse();
    final DraftTaskVariableEntity draftVariable = new DraftTaskVariableEntity();
    draftVariable.setFullValue("draft value");

    // when
    response.addDraft(draftVariable);

    // then
    assertThat(response.getDraft()).isNotNull();
    assertThat(response.getDraft().getValue()).isEqualTo("draft value");
  }
}
