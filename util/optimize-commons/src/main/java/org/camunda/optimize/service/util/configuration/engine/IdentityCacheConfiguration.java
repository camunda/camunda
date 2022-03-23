/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.util.CronNormalizerUtil;

import java.util.Optional;

@Data
@FieldNameConstants
public abstract class IdentityCacheConfiguration {
  private boolean includeUserMetaData;
  private String cronTrigger;
  private int maxPageSize;
  private long maxEntryLimit;

  public void validate() {
    if (cronTrigger == null || cronTrigger.isEmpty()) {
      throw new OptimizeConfigurationException(getConfigName() + ".cronTrigger must be set and not empty");
    }
  }

  public final void setCronTrigger(String cronTrigger) {
    this.cronTrigger = Optional.ofNullable(cronTrigger).map(CronNormalizerUtil::normalizeToSixParts).orElse(null);
  }

  @JsonIgnore
  public abstract String getConfigName();
}
