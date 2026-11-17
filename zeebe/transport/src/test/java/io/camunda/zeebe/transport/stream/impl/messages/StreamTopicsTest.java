/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl.messages;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class StreamTopicsTest {

  @ParameterizedTest
  @EnumSource(StreamTopics.class)
  void shouldPrefixTopicForDefaultTenant(final StreamTopics topic) {
    assertThat(topic.topic(DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(DEFAULT_PHYSICAL_TENANT_ID + "-" + topic.legacyTopic())
        .startsWith(DEFAULT_PHYSICAL_TENANT_ID + "-");
  }

  @ParameterizedTest
  @EnumSource(StreamTopics.class)
  void shouldPrefixTopicForNonDefaultTenant(final StreamTopics topic) {
    assertThat(topic.topic("tenant1")).startsWith("tenant1-");
  }

  @ParameterizedTest
  @EnumSource(StreamTopics.class)
  void shouldUseUnprefixedNameForLegacyTopic(final StreamTopics topic) {
    assertThat(topic.legacyTopic())
        .doesNotContain(DEFAULT_PHYSICAL_TENANT_ID + "-")
        .isNotEqualTo(topic.topic(DEFAULT_PHYSICAL_TENANT_ID));
  }
}
