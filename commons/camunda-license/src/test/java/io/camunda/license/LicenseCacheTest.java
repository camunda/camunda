/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.license;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
public class LicenseCacheTest {

  public static final String VALID_LICENSE = "valid license";

  @SystemStub private EnvironmentVariables environment;

  @Test
  public void shouldReturnTrueWhenLicenseIsValid() {
    // given
    environment.set(LicenseCache.LICENSE_ENV_VAR_KEY, VALID_LICENSE);

    assertTrue(LicenseCache.hasValidLicense());
  }
}
