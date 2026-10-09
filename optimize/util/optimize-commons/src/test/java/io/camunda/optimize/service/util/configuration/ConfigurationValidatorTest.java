/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ConfigurationValidatorTest {

  private ConfigurationService configurationService;
  private EmailAuthenticationConfiguration emailAuthConfig;
  private ElasticSearchConfiguration elasticSearchConfiguration;
  private OpenSearchConfiguration openSearchConfiguration;

  private ConfigurationValidator configurationValidator;

  @BeforeEach
  public void setup() {
    configurationService = mock(ConfigurationService.class);
    emailAuthConfig = mock(EmailAuthenticationConfiguration.class);
    elasticSearchConfiguration = mock(ElasticSearchConfiguration.class);
    openSearchConfiguration = mock(OpenSearchConfiguration.class);
    when(configurationService.getElasticSearchConfiguration())
        .thenReturn(elasticSearchConfiguration);
    when(configurationService.getOpenSearchConfiguration()).thenReturn(openSearchConfiguration);

    configurationValidator = new ConfigurationValidator();
  }

  @Test
  public void validateShouldCallEmailAuthenticationConfigurationValidate() {
    // given
    when(configurationService.getEmailAuthenticationConfiguration()).thenReturn(emailAuthConfig);

    // when
    configurationValidator.validate(configurationService);

    // then
    verify(emailAuthConfig).validate();
  }

  @Test
  public void validateShouldThrowExceptionWhenEmailAuthenticationConfigurationIsNull() {
    // given
    when(configurationService.getEmailAuthenticationConfiguration()).thenReturn(null);

    // when / then
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> configurationValidator.validate(configurationService));
  }

  @ParameterizedTest
  @ValueSource(strings = {"prefix", "hyphenated-prefix", "char+prefix"})
  void shouldAllowValidIndexPrefixes(final String testPrefix) {
    // given
    when(configurationService.getEmailAuthenticationConfiguration()).thenReturn(emailAuthConfig);
    when(elasticSearchConfiguration.getIndexPrefix()).thenReturn(testPrefix);
    when(openSearchConfiguration.getIndexPrefix()).thenReturn(testPrefix);

    // when / then
    assertThatCode(() -> configurationValidator.validate(configurationService))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"\\", "/", "*", "?", "\"", ">", "<", "|", " ", "_"})
  void shouldNotAllowInvalidCharactersInIndexPrefix(final String testCharacter) {
    // given
    when(configurationService.getEmailAuthenticationConfiguration()).thenReturn(emailAuthConfig);
    when(elasticSearchConfiguration.getIndexPrefix()).thenReturn("testPrefix" + testCharacter);
    when(openSearchConfiguration.getIndexPrefix()).thenReturn("testPrefix" + testCharacter);

    // when / then
    assertThatCode(() -> configurationValidator.validate(configurationService))
        .hasMessageContaining(
            "Optimize indexPrefix must not contain invalid characters [\\ / * ? \" < > | space _].")
        .isInstanceOf(OptimizeConfigurationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {".", "+"})
  void shouldNotAllowInvalidCharactersAtStartOfIndexPrefix(final String testCharacter) {
    // given
    when(configurationService.getEmailAuthenticationConfiguration()).thenReturn(emailAuthConfig);
    when(elasticSearchConfiguration.getIndexPrefix()).thenReturn(testCharacter + "testPrefix");
    when(openSearchConfiguration.getIndexPrefix()).thenReturn(testCharacter + "testPrefix");

    // when - then
    assertThatCode(() -> configurationValidator.validate(configurationService))
        .hasMessageContaining("Optimize indexPrefix must not begin with invalid characters [. +].")
        .isInstanceOf(OptimizeConfigurationException.class);
  }
}
