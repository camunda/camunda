/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.uiconfiguration;

import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import org.camunda.optimize.rest.cloud.CloudSaasMetaInfoService;
import org.camunda.optimize.service.SettingsService;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.UIConfigurationService;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.metadata.OptimizeVersionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ui.HeaderCustomization;
import org.camunda.optimize.service.util.configuration.ui.UIConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CLOUD_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.PLATFORM_PROFILE;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UIConfigurationServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ConfigurationService configurationService;
  @Mock
  private OptimizeVersionService versionService;
  @Mock
  private TenantService tenantService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private SettingsService settingService;
  @Mock
  private Environment environment;
  @Mock
  private Optional<CloudSaasMetaInfoService> metaInfoService = Optional.empty();

  @InjectMocks
  UIConfigurationService underTest;

  @ParameterizedTest
  @MethodSource("optimizeProfiles")
  public void testProfileReadCorrectly(final String activeProfile) {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[]{activeProfile});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.getOptimizeProfile()).isEqualTo(activeProfile);
  }

  @Test
  public void testDefaultProfileUsed() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[]{});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.getOptimizeProfile()).isEqualTo(PLATFORM_PROFILE);
  }

  @Test
  public void testMultipleProfilesDoesNotWork() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[]{CLOUD_PROFILE, CCSM_PROFILE});

    // then
    assertThatThrownBy(() -> underTest.getUIConfiguration())
      .isInstanceOf(OptimizeConfigurationException.class)
      .hasMessage("Cannot configure more than one profile for Optimize");
  }

  @Test
  public void testInvalidProfilesDoesNotWork() {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[]{"someUnknownProfile"});

    // then
    assertThatThrownBy(() -> underTest.getUIConfiguration())
      .isInstanceOf(OptimizeConfigurationException.class)
      .hasMessage("Invalid profile configured");
  }

  @ParameterizedTest
  @MethodSource("optimizeProfilesAndExpectedIsEnterpriseModeResult")
  public void testIsEnterpriseModeDeterminedCorrectly(final String activeProfile,
                                                      final boolean expectedIsEnterprise) {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[]{activeProfile});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.isEnterpriseMode()).isEqualTo(expectedIsEnterprise);
  }

  private static Stream<Arguments> optimizeProfiles() {
    return Stream.of(
      Arguments.of(CLOUD_PROFILE),
      Arguments.of(CCSM_PROFILE),
      Arguments.of(PLATFORM_PROFILE)
    );
  }

  private static Stream<Arguments> optimizeProfilesAndExpectedIsEnterpriseModeResult() {
    return Stream.of(
      Arguments.of(CLOUD_PROFILE, true),
      Arguments.of(CCSM_PROFILE, false), // false by default because it's not mocked
      Arguments.of(PLATFORM_PROFILE, true)
    );
  }

  private void initializeMocks() {
    HeaderCustomization headerCustomization = new HeaderCustomization();
    headerCustomization.setPathToLogoIcon("logo/camunda_icon.svg");
    headerCustomization.setBackgroundColor("#FFFFFF");
    UIConfiguration uiConfiguration = new UIConfiguration();
    uiConfiguration.setHeader(headerCustomization);
    when(configurationService.getUiConfiguration()).thenReturn(uiConfiguration);
    when(configurationService.getConfiguredWebhooks()).thenReturn(Collections.emptyMap());
  }

}
