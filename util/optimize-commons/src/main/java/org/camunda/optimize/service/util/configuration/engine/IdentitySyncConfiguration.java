/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.engine;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.CronNormalizerUtil;

import java.util.Optional;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.IDENTITY_SYNC_CONFIGURATION;

@Data
@FieldNameConstants(asEnum = true)
public class IdentitySyncConfiguration {
  private boolean includeUserMetaData;
  private String cronTrigger;
  private int maxPageSize;
  private long maxEntryLimit;

  public void validate() {
    if (cronTrigger == null || cronTrigger.isEmpty()) {
      throw new OptimizeConfigurationException(IDENTITY_SYNC_CONFIGURATION + ".cronTrigger must be set and not empty");
    }
  }

  public final void setCronTrigger(String cronTrigger) {
    this.cronTrigger = Optional.ofNullable(cronTrigger).map(CronNormalizerUtil::normalizeToSixParts).orElse(null);
  }
}
