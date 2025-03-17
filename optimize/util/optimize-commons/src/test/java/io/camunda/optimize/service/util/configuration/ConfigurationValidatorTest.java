/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

public class ConfigurationValidatorTest {

  @Test
  public void validateShouldCallEmailAuthenticationConfigurationValidate() {
    // given
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    final EmailAuthenticationConfiguration emailAuthConfig =
        mock(EmailAuthenticationConfiguration.class);
    when(configurationService.getEmailAuthenticationConfiguration()).thenReturn(emailAuthConfig);
    final ConfigurationValidator validator = new ConfigurationValidator();

    // when
    validator.validate(configurationService);

    // then
    verify(emailAuthConfig).validate();
  }

  @Test
  public void validateShouldThrowExceptionWhenEmailAuthenticationConfigurationIsNull() {
    // given
    final ConfigurationService configurationService = mock(ConfigurationService.class);
    when(configurationService.getEmailAuthenticationConfiguration()).thenReturn(null);
    final ConfigurationValidator validator = new ConfigurationValidator();

    // when / then
    assertThrows(NullPointerException.class, () -> validator.validate(configurationService));
  }
}
