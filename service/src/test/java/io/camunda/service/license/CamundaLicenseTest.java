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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class CamundaLicenseTest {

  @Test
  public void shouldReturnTrueWhenLicenseIsValid() throws InvalidLicenseException {
    final CamundaLicense testLicense = spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    assertTrue(testLicense.determineLicenseValidity("mocked"));
  }

  @Test
  public void shouldReturnFalseWhenLicenseIsInvalid() throws InvalidLicenseException {
    final CamundaLicense testLicense = spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    doReturn(mockKey).when(testLicense).getLicenseKey(anyString());
    Mockito.doThrow(new InvalidLicenseException("test exception!")).when(mockKey).validate();

    assertFalse(testLicense.determineLicenseValidity("mocked"));
  }
}
