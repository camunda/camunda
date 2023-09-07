/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.conditionals;

import org.camunda.optimize.service.util.configuration.ConfigurationService;

public class OpenSearchCondition extends DataBaseCondition {
  private static final String DATABASE = "opensearch";
  protected final ConfigurationService confService;

    public OpenSearchCondition(ConfigurationService configurationService) {
        super(configurationService);
      this.confService = configurationService;
    }

    @Override
  public boolean getDefaultIfEmpty() {
    return false;
  }

  @Override
  public String getDatabase() {
    return DATABASE;
  }
}
