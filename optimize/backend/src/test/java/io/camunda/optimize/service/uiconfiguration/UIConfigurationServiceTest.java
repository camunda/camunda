/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.uiconfiguration;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CAMUNDA_OPTIMIZE_DATABASE;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CLOUD_PROFILE;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.ELASTICSEARCH_DATABASE_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.users.Users;
import io.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import io.camunda.optimize.license.LicenseType;
import io.camunda.optimize.rest.cloud.CloudSaasMetaInfoService;
import io.camunda.optimize.service.CamundaLicenseService;
import io.camunda.optimize.service.SettingsService;
import io.camunda.optimize.service.UIConfigurationService;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.metadata.OptimizeVersionService;
import io.camunda.optimize.service.tenant.TenantService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.configuration.OptimizeProfile;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UIConfigurationServiceTest {

  private static OffsetDateTime testDate;
  private static final String TEST_DATE_STRING = "2024-10-29T15:14:13Z";
  @InjectMocks UIConfigurationService underTest;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ConfigurationService configurationService;

  @Mock private OptimizeVersionService versionService;
  @Mock private TenantService tenantService;
  @Mock private CamundaLicenseService camundaLicenseService;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private SettingsService settingService;

  @Mock private Environment environment;
  @Mock private Optional<CloudSaasMetaInfoService> metaInfoService = Optional.empty();
  @Mock private Identity identity;
  @Mock private Users identityUsers;

  private static Stream<Arguments> optimizeProfiles() {
    return Stream.of(Arguments.of(CLOUD_PROFILE), Arguments.of(CCSM_PROFILE));
  }

  private static Stream<Arguments> optimizeProfilesAndExpectedIsEnterpriseModeResult() {
    return Stream.of(
        Arguments.of(CLOUD_PROFILE, true),
        Arguments.of(CCSM_PROFILE, false)); // false by default because it's not mocked
  }

  @BeforeAll
  public static void beforeAll() {
    testDate = OffsetDateTime.parse(TEST_DATE_STRING);
  }

  @ParameterizedTest
  @MethodSource("optimizeProfiles")
  public void testProfileReadCorrectly(final String activeProfile) {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {activeProfile});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.getOptimizeProfile())
        .isEqualTo(OptimizeProfile.toProfile(activeProfile));
  }

  @Test
  public void testDefaultProfileUsed() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.getOptimizeProfile()).isEqualTo(OptimizeProfile.CCSM);
  }

  @Test
  public void testMultipleProfilesDoesNotWork() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {CLOUD_PROFILE, CCSM_PROFILE});

    // then
    assertThatThrownBy(() -> underTest.getUIConfiguration())
        .isInstanceOf(OptimizeConfigurationException.class)
        .hasMessage("Cannot configure more than one Optimize profile");
  }

  @Test
  public void testUnknownProfileUsesDefault() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {"someUnknownProfile"});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.getOptimizeProfile()).isEqualTo(OptimizeProfile.CCSM);
  }

  @ParameterizedTest
  @MethodSource("optimizeProfilesAndExpectedIsEnterpriseModeResult")
  public void testIsEnterpriseModeDeterminedCorrectly(
      final String activeProfile, final boolean expectedIsEnterprise) {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {activeProfile});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.isEnterpriseMode()).isEqualTo(expectedIsEnterprise);
  }

  @Test
  public void testMaximumNumberOfDataSourcesForReportIsReturned() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {CLOUD_PROFILE});
    when(configurationService.getUiConfiguration().getMaxNumDataSourcesForReport()).thenReturn(50);

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.getMaxNumDataSourcesForReport()).isEqualTo(50);
  }

  @Test
  public void testCamundaLicenseInformationIsReturned() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {CLOUD_PROFILE});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.getLicenseType()).isEqualTo("saas");
    assertThat(configurationResponse.isValidLicense()).isEqualTo(true);
    assertThat(configurationResponse.isCommercial()).isEqualTo(false);
    assertThat(configurationResponse.getExpiresAt()).isEqualTo(TEST_DATE_STRING);
  }

  @Test
  public void testCamundaLicenseReturnsNullWhenThereIsNoExpiration() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[] {CLOUD_PROFILE});
    when(camundaLicenseService.getCamundaLicenseExpiresAt()).thenReturn(null);

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.getLicenseType()).isEqualTo("saas");
    assertThat(configurationResponse.isValidLicense()).isEqualTo(true);
    assertThat(configurationResponse.isCommercial()).isEqualTo(false);
    assertThat(configurationResponse.getExpiresAt()).isEqualTo(null);
  }

  private void initializeMocks() {
    when(identity.users()).thenReturn(identityUsers);
    when(identityUsers.isAvailable()).thenReturn(true);
    when(environment.getProperty(CAMUNDA_OPTIMIZE_DATABASE, ELASTICSEARCH_DATABASE_PROPERTY))
        .thenReturn(DatabaseType.ELASTICSEARCH.toString());
    when(camundaLicenseService.getCamundaLicenseType()).thenReturn(LicenseType.SAAS);
    when(camundaLicenseService.isCamundaLicenseValid()).thenReturn(true);
    when(camundaLicenseService.isCommercialCamundaLicense()).thenReturn(false);
    when(camundaLicenseService.getCamundaLicenseExpiresAt()).thenReturn(testDate);
  }
}
