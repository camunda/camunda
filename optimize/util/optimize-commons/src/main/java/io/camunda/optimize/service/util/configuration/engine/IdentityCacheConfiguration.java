/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.CronNormalizerUtil;
import java.util.Optional;
import lombok.Data;

@Data
public abstract class IdentityCacheConfiguration {

  private boolean includeUserMetaData;
  private boolean collectionRoleCleanupEnabled;
  private String cronTrigger;
  private int maxPageSize;
  private long maxEntryLimit;

  public void validate() {
    if (cronTrigger == null || cronTrigger.isEmpty()) {
      throw new OptimizeConfigurationException(
          getConfigName() + ".cronTrigger must be set and not empty");
    }
  }

  public final void setCronTrigger(final String cronTrigger) {
    this.cronTrigger =
        Optional.ofNullable(cronTrigger).map(CronNormalizerUtil::normalizeToSixParts).orElse(null);
  }

  @JsonIgnore
  public abstract String getConfigName();

  public static final class Fields {

    public static final String includeUserMetaData = "includeUserMetaData";
    public static final String collectionRoleCleanupEnabled = "collectionRoleCleanupEnabled";
    public static final String cronTrigger = "cronTrigger";
    public static final String maxPageSize = "maxPageSize";
    public static final String maxEntryLimit = "maxEntryLimit";
  }
}
