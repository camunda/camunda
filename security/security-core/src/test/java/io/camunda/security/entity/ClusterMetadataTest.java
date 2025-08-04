/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.security.entity.ClusterMetadata.AppName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClusterMetadataTest {

  @Nested
  class AppNameTest {

    @Test
    void getValue() throws Exception {
      for (final AppName appName : AppName.values()) {
        final JsonProperty jsonProperty =
            AppName.class.getField(appName.name()).getAnnotation(JsonProperty.class);
        assertThat(appName.getValue()).isEqualTo(jsonProperty.value());
      }
    }
  }
}
