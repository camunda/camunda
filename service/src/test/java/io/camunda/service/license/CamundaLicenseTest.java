/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.license;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class CamundaLicenseTest {
  @Test
  public void shouldReturnFalseWhenLicenseIsInvalid() throws InvalidLicenseException {
    final CamundaLicense testLicense = mock(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    when(testLicense.isValid()).thenCallRealMethod();
    Mockito.doCallRealMethod().when(testLicense).initializeStoredLicense();
    when(testLicense.getEnvironmentVariableValue(anyString())).thenReturn("some license str");
    Mockito.doCallRealMethod().when(testLicense).determineLicenseValidity(anyString());
    when(testLicense.getLicenseKey(anyString())).thenReturn(mockKey);

    Mockito.doThrow(new InvalidLicenseException("test exception!")).when(mockKey).validate();

    assertFalse(testLicense.isValid());
  }

  @Test
  public void shouldReturnTryeWhenLicenseIsValid() throws InvalidLicenseException {
    final CamundaLicense testLicense = mock(CamundaLicense.class);

    when(testLicense.isValid()).thenCallRealMethod();
    Mockito.doCallRealMethod().when(testLicense).initializeStoredLicense();
    when(testLicense.getEnvironmentVariableValue(anyString())).thenReturn("some license str");
    Mockito.doCallRealMethod().when(testLicense).determineLicenseValidity(anyString());
    when(testLicense.getLicenseKey(anyString())).thenReturn(mock(LicenseKey.class));

    assertTrue(testLicense.isValid());
  }
}
