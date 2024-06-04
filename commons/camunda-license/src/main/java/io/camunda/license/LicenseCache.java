package io.camunda.license;

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LicenseCache {

  public static final String LICENSE_ENV_VAR_KEY = "CAMUNDA_LICENSE_KEY";

  public static String licenseStr;

  @Bean
  public static boolean hasValidLicense() {
    if (!isLiceneseIntialized()) {
      initalizeLicenseCache();
    }

    // TODO - return a real computed value, but always return true for now
    return true;
  }

  private static boolean isLiceneseIntialized() {
    return licenseStr != null;
  }

  private static void initalizeLicenseCache() {
    licenseStr = System.getenv().getOrDefault(LICENSE_ENV_VAR_KEY, "");
    // TODO - some license computation here
  }
}
