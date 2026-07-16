/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

public class ClusterVariable {

  private static final String PREFIX = "camunda.api.rest.cluster-variable";

  /**
   * Maximum allowed size (in UTF-8 bytes) of a cluster variable's serialized metadata JSON. When
   * unset, the built-in default is used.
   */
  private Integer maxMetadataSize;

  public Integer getMaxMetadataSize() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".max-metadata-size",
        maxMetadataSize,
        Integer.class,
        BackwardsCompatibilityMode.NOT_SUPPORTED,
        Set.of());
  }

  public void setMaxMetadataSize(final Integer maxMetadataSize) {
    this.maxMetadataSize = maxMetadataSize;
  }
}
