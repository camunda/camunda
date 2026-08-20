/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.util.Date;
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
    when(mockKey.getValidUntil()).thenReturn(new Date());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertThat(testLicense.isValid()).isTrue();
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
    assertThat(testLicense.isValid()).isFalse();
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
    when(mockKey.getValidUntil()).thenReturn(new Date());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertThat(testLicense.getLicenseType()).isEqualTo(LicenseType.PRODUCTION);
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
    assertThat(testLicense.getLicenseType()).isEqualTo(LicenseType.UNKNOWN);
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
    when(mockKey.getValidUntil()).thenReturn(new Date());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertThat(testLicense.getLicenseType()).isEqualTo(LicenseType.SAAS);
    assertThat(testLicense.isValid()).isTrue();
  }

  @Test
  public void shouldReturnTrueForIsCommercialWhenLicenseIsNotNonCommercial()
      throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = Mockito.spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    final Map<String, String> testProperties = new HashMap<>();
    testProperties.put(LICENSE_TYPE_KEY, PRODUCTION_LICENSE_TYPE);
    when(mockKey.getProperties()).thenReturn(testProperties);
    when((mockKey.isCommercial())).thenReturn(true);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertThat(testLicense.isCommercial()).isTrue();
  }

  @Test
  public void shouldReturnFalseForIsCommercialWhenLicenseIsNonCommercial()
      throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = Mockito.spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    final Map<String, String> testProperties = new HashMap<>();
    testProperties.put(LICENSE_TYPE_KEY, PRODUCTION_LICENSE_TYPE);
    when(mockKey.getProperties()).thenReturn(testProperties);
    when((mockKey.isCommercial())).thenReturn(false);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertThat(testLicense.isCommercial()).isFalse();
  }

  @Test
  public void shouldReturnExpiryDateForLicense() throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = Mockito.spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    final Map<String, String> testProperties = new HashMap<>();
    testProperties.put(LICENSE_TYPE_KEY, PRODUCTION_LICENSE_TYPE);
    when(mockKey.getProperties()).thenReturn(testProperties);

    final Date testDate = new Date();
    when(mockKey.getValidUntil()).thenReturn(testDate);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertThat(testLicense.expiresAt()).isEqualTo(testDate.toInstant().atOffset(ZoneOffset.UTC));
  }

  @Test
  public void shouldReturnNullWhenThereIsNoExpirationDate() throws InvalidLicenseException {
    // given
    final CamundaLicense testLicense = Mockito.spy(CamundaLicense.class);
    final LicenseKey mockKey = mock(LicenseKey.class);

    final Map<String, String> testProperties = new HashMap<>();
    testProperties.put(LICENSE_TYPE_KEY, PRODUCTION_LICENSE_TYPE);
    when(mockKey.getProperties()).thenReturn(testProperties);

    when(mockKey.getValidUntil()).thenReturn(null);

    Mockito.doReturn(mockKey).when(testLicense).getLicenseKey(anyString());

    // when
    testLicense.initializeWithLicense(TEST_LICENSE);

    // then
    assertThat(testLicense.expiresAt()).isNull();
  }
}
