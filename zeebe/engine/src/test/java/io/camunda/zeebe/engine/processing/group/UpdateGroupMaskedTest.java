/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord.GroupAttribute;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class UpdateGroupMaskedTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldPersistExternalIdOnCreate() {
    // when
    final var created =
        ENGINE
            .group()
            .newGroup(UUID.randomUUID().toString())
            .withName("Group")
            .withExternalId("ext-1")
            .create();

    // then
    assertThat(created.getValue().getExternalId()).isEqualTo("ext-1");
  }

  @Test
  public void shouldCreateGroupWithEmptyExternalIdByDefault() {
    // when
    final var created =
        ENGINE.group().newGroup(UUID.randomUUID().toString()).withName("Group").create();

    // then
    assertThat(created.getValue().getExternalId()).isEmpty();
  }

  @Test
  public void shouldOnlyChangeMaskedAttributes() {
    // given
    final var groupId = UUID.randomUUID().toString();
    ENGINE
        .group()
        .newGroup(groupId)
        .withName("Group")
        .withDescription("Description")
        .withExternalId("ext-1")
        .create();

    // when
    final var updated =
        ENGINE
            .group()
            .updateGroup(groupId)
            .withName("Renamed")
            .withChangedAttributes(GroupAttribute.NAME)
            .update()
            .getValue();

    // then
    assertThat(updated.getName()).isEqualTo("Renamed");
    assertThat(updated.getDescription()).isEqualTo("Description");
    assertThat(updated.getExternalId()).isEqualTo("ext-1");
  }

  @Test
  public void shouldSetExternalIdAndClearDescriptionWithEmptyValue() {
    // given
    final var groupId = UUID.randomUUID().toString();
    ENGINE.group().newGroup(groupId).withName("Group").withDescription("Description").create();

    // when
    final var updated =
        ENGINE
            .group()
            .updateGroup(groupId)
            .withDescription("")
            .withExternalId("ext-2")
            .withChangedAttributes(GroupAttribute.DESCRIPTION, GroupAttribute.EXTERNAL_ID)
            .update()
            .getValue();

    // then
    assertThat(updated.getDescription()).isEmpty();
    assertThat(updated.getExternalId()).isEqualTo("ext-2");
    assertThat(updated.getName()).isEqualTo("Group");
  }

  @Test
  public void shouldKeepExternalIdOnLegacyUpdate() {
    // given
    final var groupId = UUID.randomUUID().toString();
    ENGINE
        .group()
        .newGroup(groupId)
        .withName("Group")
        .withDescription("Description")
        .withExternalId("ext-1")
        .create();

    // when
    final var updated =
        ENGINE
            .group()
            .updateGroup(groupId)
            .withName("Renamed")
            .withDescription("New description")
            .update()
            .getValue();

    // then
    assertThat(updated.getName()).isEqualTo("Renamed");
    assertThat(updated.getDescription()).isEqualTo("New description");
    assertThat(updated.getExternalId()).isEqualTo("ext-1");
  }

  @Test
  public void shouldWriteUpdatedEventWithoutMask() {
    // given
    final var groupId = UUID.randomUUID().toString();
    ENGINE.group().newGroup(groupId).withName("Group").create();

    // when
    ENGINE
        .group()
        .updateGroup(groupId)
        .withName("Renamed")
        .withChangedAttributes(GroupAttribute.NAME)
        .update();

    // then
    final var event =
        RecordingExporter.groupRecords(GroupIntent.UPDATED).withGroupId(groupId).getFirst();
    assertThat(((GroupRecord) event.getValue()).getChangedAttributes()).isEmpty();
  }

  @Test
  public void shouldPersistAttributesOnCreate() {
    // when
    final var created =
        ENGINE
            .group()
            .newGroup(UUID.randomUUID().toString())
            .withName("Group")
            .withAttributes(Map.of("costCenter", "cc-1"))
            .create();

    // then
    assertThat(created.getValue().getAttributes())
        .containsExactlyEntriesOf(Map.of("costCenter", "cc-1"));
  }

  @Test
  public void shouldReplaceAttributesWhenMasked() {
    // given
    final var groupId = UUID.randomUUID().toString();
    ENGINE
        .group()
        .newGroup(groupId)
        .withName("Group")
        .withAttributes(Map.of("costCenter", "cc-1", "region", "emea"))
        .create();

    // when
    final var updated =
        ENGINE
            .group()
            .updateGroup(groupId)
            .withAttributes(Map.of("owner", "alice"))
            .withChangedAttributes(GroupAttribute.ATTRIBUTES)
            .update()
            .getValue();

    // then
    assertThat(updated.getAttributes()).containsExactlyEntriesOf(Map.of("owner", "alice"));
    assertThat(updated.getName()).isEqualTo("Group");
  }

  @Test
  public void shouldMergeAttributesWhenMasked() {
    // given
    final var groupId = UUID.randomUUID().toString();
    ENGINE
        .group()
        .newGroup(groupId)
        .withName("Group")
        .withAttributes(Map.of("costCenter", "cc-1", "region", "emea"))
        .create();
    final var patch = new HashMap<String, Object>();
    patch.put("costCenter", "cc-2");
    patch.put("region", null);
    patch.put("owner", "alice");

    // when
    final var updated =
        ENGINE
            .group()
            .updateGroup(groupId)
            .withAttributes(patch)
            .withChangedAttributes(GroupAttribute.ATTRIBUTES_MERGE)
            .update()
            .getValue();

    // then
    assertThat(updated.getAttributes())
        .containsExactlyInAnyOrderEntriesOf(Map.of("costCenter", "cc-2", "owner", "alice"));
  }

  @Test
  public void shouldKeepAttributesOnLegacyUpdate() {
    // given
    final var groupId = UUID.randomUUID().toString();
    ENGINE
        .group()
        .newGroup(groupId)
        .withName("Group")
        .withAttributes(Map.of("costCenter", "cc-1"))
        .create();

    // when
    final var updated = ENGINE.group().updateGroup(groupId).withName("Renamed").update().getValue();

    // then
    assertThat(updated.getName()).isEqualTo("Renamed");
    assertThat(updated.getAttributes()).containsExactlyEntriesOf(Map.of("costCenter", "cc-1"));
  }
}
