/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.uiconfiguration;

import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import org.camunda.optimize.service.SettingsService;
import org.camunda.optimize.service.TenantService;
import org.camunda.optimize.service.UIConfigurationService;
import org.camunda.optimize.service.metadata.OptimizeVersionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ui.HeaderCustomization;
import org.camunda.optimize.service.util.configuration.ui.UIConfiguration;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.OffsetDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UIConfigurationServiceTest {

  @Mock
  private ConfigurationService configurationService;
  @Mock
  private OptimizeVersionService versionService;
  @Mock
  private TenantService tenantService;
  @Mock
  private SettingsService settingService;
  @Mock
  private Environment environment;

  @InjectMocks
  UIConfigurationService underTest;

  @ParameterizedTest
  @MethodSource("profilesAndExpectedCloudEnabledSetting")
  public void testCloudProfileReadCorrectly(final String activeProfile, final boolean expectedCloudEnabled) {
    // given
    initializeMocks();
    when(environment.getActiveProfiles()).thenReturn(new String[]{activeProfile});

    // when
    final UIConfigurationResponseDto configurationResponse = underTest.getUIConfiguration();

    // then
    assertThat(configurationResponse.isOptimizeCloudEnvironment()).isEqualTo(expectedCloudEnabled);
  }

  private static Stream<Arguments> profilesAndExpectedCloudEnabledSetting() {
    return Stream.of(
      Arguments.of("cloud", true),
      Arguments.of("someOtherProfile", false),
      Arguments.of(null, false)
    );
  }

  private void initializeMocks() {
    HeaderCustomization headerCustomization = new HeaderCustomization();
    headerCustomization.setPathToLogoIcon("logo/camunda_icon.svg");
    headerCustomization.setBackgroundColor("#FFFFFF");
    UIConfiguration uiConfiguration = new UIConfiguration();
    uiConfiguration.setHeader(headerCustomization);
    when(settingService.getSettings()).thenReturn(
      new SettingsResponseDto(true, "omran", OffsetDateTime.now()));
    when(configurationService.getUiConfiguration()).thenReturn(uiConfiguration);
  }

}
