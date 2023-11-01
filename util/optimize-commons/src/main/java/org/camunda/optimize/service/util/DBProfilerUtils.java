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
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.optimizeDatabaseProfiles;

public class DBProfilerUtils {

  public static String findOutDatabaseProfile(Environment env) {
    final List<String> databaseProfilesFound = Arrays.stream(env.getActiveProfiles())
      .filter(optimizeDatabaseProfiles::contains).toList();
    if (databaseProfilesFound.size() != 1){
//      TODO it would be fixed once we move our integration tests to the profile agnostic approach OPT-7225
//      throw new OptimizeNotSpecifiedDatabaseProfileException(
//        "There is no specified database profile. You have to put 'elasticsearch' or 'opensearch' into env variables");
      return ELASTICSEARCH_PROFILE;
    }
    return databaseProfilesFound.get(0);
  }
}
