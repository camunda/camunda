/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.security.api.model.config.SaasConfiguration;
import org.jspecify.annotations.Nullable;

public final class SaasConfigurationHelper {

  private SaasConfigurationHelper() {}

  public static @Nullable String organizationId(final @Nullable SaasConfiguration saas) {
    return saas != null ? saas.getOrganizationId() : null;
  }

  public static @Nullable String clusterId(final @Nullable SaasConfiguration saas) {
    return saas != null ? saas.getClusterId() : null;
  }

  public static boolean isSaas(final @Nullable SaasConfiguration saas) {
    return clusterId(saas) != null;
  }
}
