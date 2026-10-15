/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord.GroupAttribute;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GroupRecordTest {

  @Test
  void shouldDefaultToEmptyExternalIdAndMask() {
    // when
    final var record = new GroupRecord().setGroupId("group");

    // then
    assertThat(record.getExternalId()).isEmpty();
    assertThat(record.getChangedAttributes()).isEmpty();
  }

  @Test
  void shouldRoundTripChangedAttributesAndExternalId() {
    // given
    final var record =
        new GroupRecord()
            .setGroupId("group")
            .setExternalId("ext-1")
            .setChangedAttributes(Set.of(GroupAttribute.NAME, GroupAttribute.EXTERNAL_ID));

    // when
    final var copy = new GroupRecord();
    copy.copyFrom(record);

    // then
    assertThat(copy.getExternalId()).isEqualTo("ext-1");
    assertThat(copy.getChangedAttributes())
        .containsExactlyInAnyOrder(GroupAttribute.NAME, GroupAttribute.EXTERNAL_ID);
    assertThat(copy.clearChangedAttributes().getChangedAttributes()).isEmpty();
  }

  @Test
  void shouldDefaultToEmptyEntityIds() {
    // when
    final var record = new GroupRecord().setGroupId("group");

    // then
    assertThat(record.getEntityIds()).isEmpty();
  }

  @Test
  void shouldRoundTripEntityIds() {
    // given
    final var record = new GroupRecord().setGroupId("group").setEntityIds(List.of("a", "b"));

    // when
    final var copy = new GroupRecord();
    copy.copyFrom(record);

    // then
    assertThat(copy.getEntityIds()).containsExactly("a", "b");
    assertThat(copy.setEntityIds(List.of()).getEntityIds()).isEmpty();
  }

  @Test
  void shouldRoundTripAttributes() {
    // given
    final var attributes = Map.of("costCenter", "cc-1", "tags", List.of("a", "b"));
    final var record = new GroupRecord().setGroupId("group").setAttributes(attributes);

    // when
    final var copy = new GroupRecord();
    copy.copyFrom(record);

    // then
    assertThat(copy.getAttributes()).isEqualTo(attributes);
    assertThat(new GroupRecord().getAttributes()).isEmpty();
  }
}
