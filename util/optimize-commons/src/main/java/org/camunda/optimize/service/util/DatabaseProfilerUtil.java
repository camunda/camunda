/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.OPENSEARCH_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.optimizeDatabaseProfiles;

public class DatabaseProfilerUtil {

  public static String getDatabaseProfile(final Environment environment) {
    List<String> databaseProfilesFound = Arrays.stream(environment.getActiveProfiles())
      .filter(optimizeDatabaseProfiles::contains)
      .toList();
    if (databaseProfilesFound.size() == 1 && databaseProfilesFound.get(0).equals(OPENSEARCH_PROFILE)) {
      return OPENSEARCH_PROFILE;
    } else {
      return ELASTICSEARCH_PROFILE;
    }
  }

}
