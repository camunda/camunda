/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.camunda.bpm.licensecheck.InvalidLicenseException;
import org.camunda.bpm.licensecheck.LicenseKey;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class CamundaLicenseTest {

  private static final String TEST_LICENSE = "whatever";
  private static final String LICENSE_TYPE_KEY = "licenseType";
  private static final String PRODUCTION_LICENSE_TYPE = "production";
  private static final String SAAS_LICENSE_TYPE = "saas";
  private static final String TEST_EXCEPTION_MESSAGE = "test exception";

  @Test
  public void shouldReturnTrueFromIsValidWhenLicenseIsValid() throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = Mockito.spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    final Map<String, String> testProperties = new HashMap<>();
    testProperties.put(LICENSE_TYPE_KEY, PRODUCTION_LICENSE_TYPE);
    when(mockKey.getProperties()).thenReturn(testProperties);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertTrue(testLicense.isValid());
  }

  @Test
  public void shouldReturnFalseFromIsValidWhenLicenseIsInvalid() throws InvalidLicenseException {
    final CamundaLicense testLicense = Mockito.spy(CamundaLicense.class);
    // given
    Mockito.doThrow(new InvalidLicenseException(TEST_EXCEPTION_MESSAGE))
        .when(testLicense)
        .getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertFalse(testLicense.isValid());
  }

  @Test
  public void shouldReturnProperLicenseTypeFromLicenseProperty() throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = Mockito.spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    final Map<String, String> testProperties = new HashMap<>();
    testProperties.put(LICENSE_TYPE_KEY, PRODUCTION_LICENSE_TYPE);
    when(mockKey.getProperties()).thenReturn(testProperties);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertEquals(LicenseType.PRODUCTION, testLicense.getLicenseType());
  }

  @Test
  public void shouldReturnUnknownWhenLicensePropertyDoesNotExist() throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = Mockito.spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    final Map<String, String> testProperties = new HashMap<>();
    when(mockKey.getProperties()).thenReturn(testProperties);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertEquals(LicenseType.UNKNOWN, testLicense.getLicenseType());
  }

  @Test
  public void shouldReturnProperValidValuesWhenLicenseIsSaas() throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = Mockito.spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    final Map<String, String> testProperties = new HashMap<>();
    testProperties.put(LICENSE_TYPE_KEY, SAAS_LICENSE_TYPE);
    when(mockKey.getProperties()).thenReturn(testProperties);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertEquals(LicenseType.SAAS, testLicense.getLicenseType());
    assertTrue(testLicense.isValid());
  }
}
