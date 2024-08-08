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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class CamundaLicenseTest {

  @Test
  public void shouldReturnTrueFromIsValidWhenLicenseIsValid() throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense("whatever");

    // then
    assertTrue(testLicense.isValid());
  }

  @Test
  public void shouldReturnFalseFromIsValidWhenLicenseIsInvalid() throws InvalidLicenseException {
    final CamundaLicense testLicense = spy(CamundaLicense.class);
    // given
    Mockito.doThrow(new InvalidLicenseException("test exception"))
        .when(testLicense)
        .getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense("whatever");

    // then
    assertFalse(testLicense.isValid());
  }

  @Test
  public void shouldReturnTrueFromIsSelfManagedWhenPropertyIsSetToSelfManaged()
      throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    final Map<String, String> testProperties = new HashMap<>();
    testProperties.put("environmentMode", "self-managed");
    when(mockKey.getProperties()).thenReturn(testProperties);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense("whatever");

    // then
    assertTrue(testLicense.isSelfManaged());
  }

  @Test
  public void shouldReturnTrueFromIsSelfManagedWhenPropertyDoesNotExist()
      throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    final Map<String, String> testProperties = new HashMap<>();
    when(mockKey.getProperties()).thenReturn(testProperties);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense("whatever");

    // then
    assertTrue(testLicense.isSelfManaged());
  }

  @Test
  public void shouldReturnProperValidValuesWhenLicenseIsSaas() throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    final Map<String, String> testProperties = new HashMap<>();
    testProperties.put("licenseType", "saas");
    when(mockKey.getProperties()).thenReturn(testProperties);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense("whatever");

    // then
    assertFalse(testLicense.isSelfManaged());
    assertTrue(testLicense.isValid());
  }
}
