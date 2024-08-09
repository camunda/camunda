/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.license;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class CamundaLicenseTest {
  public static final String VALID_LICENSE = "valid license";

  @Test
  public void shouldReturnTrueWhenLicenseIsValid() {
    // given
    final CamundaLicense camundaLicense =
        new CamundaLicense(new MockEnvironmentVariableReaderWithValidLicense());

    assertTrue(camundaLicense.isValid());
  }

  private static final class MockEnvironmentVariableReaderWithValidLicense
      extends EnvironmentVariableReader {
    @Override
    public String getEnvironmentVariableValue(final String envVarName) {
      return VALID_LICENSE;
    }
  }
}
