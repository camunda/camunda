/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade;

import io.camunda.optimize.upgrade.service.UpgradeValidationService;
import java.io.File;
import java.io.FileWriter;
import java.net.URISyntaxException;

public class EnvironmentConfigUtil {

  private EnvironmentConfigUtil() {
    super();
  }

  public static void createEmptyEnvConfig() throws Exception {
    createEnvConfig("");
  }

  public static void createEnvConfig(String content) throws Exception {
    File env = getClasspathFolder();
    File config = new File(env.getAbsolutePath() + "/environment-config.yaml");

    if (!config.exists()) {
      config.createNewFile();
      FileWriter fileWriter = new FileWriter(config);
      if (content != null && !content.isEmpty()) {
        fileWriter.append(content);
      } else {
        fileWriter.write("");
      }
      fileWriter.close();
    }
  }

  public static void deleteEnvConfig() throws Exception {
    File env = getClasspathFolder();
    File config = new File(env.getAbsolutePath() + "/environment-config.yaml");

    if (config.exists()) {
      config.delete();
    }
  }

  private static File getClasspathFolder() throws URISyntaxException {
    String executionFolderPath =
        UpgradeValidationService.class
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toURI()
            .getPath();
    return new File(executionFolderPath);
  }
}
