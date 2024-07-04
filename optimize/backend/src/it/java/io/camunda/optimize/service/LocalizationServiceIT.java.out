/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.junit.jupiter.api.Assertions.assertThrows;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class LocalizationServiceIT extends AbstractPlatformIT {
//
//  @Test
//  public void failOnMissingFileForAvailableLocales() {
//    // given
//    embeddedOptimizeExtension.getConfigurationService().getAvailableLocales().add("xyz");
//
//    // then
//    final OptimizeConfigurationException exception =
//        assertThrows(
//            OptimizeConfigurationException.class,
//            () -> embeddedOptimizeExtension.reloadConfiguration());
//    assertThat(exception).isNotNull();
//    assertThat(exception.getMessage()).contains("xyz.json]");
//  }
//
//  @Test
//  public void failOnInvalidJsonFileForAvailableLocales() {
//    // given
//    embeddedOptimizeExtension.getConfigurationService().getAvailableLocales().add("invalid");
//
//    // then
//    final OptimizeConfigurationException exception =
//        assertThrows(
//            OptimizeConfigurationException.class,
//            () -> embeddedOptimizeExtension.reloadConfiguration());
//    assertThat(exception).isNotNull();
//    assertThat(exception.getMessage())
//        .contains(" not a valid JSON file [localization/invalid.json]");
//  }
//
//  @Test
//  public void failOnFallbackLocaleNotPresentInAvailableLocales() {
//    // given
//    embeddedOptimizeExtension.getConfigurationService().setFallbackLocale("xyz");
//
//    // then
//    final OptimizeConfigurationException exception =
//        assertThrows(
//            OptimizeConfigurationException.class,
//            () -> embeddedOptimizeExtension.reloadConfiguration());
//    assertThat(exception).isNotNull();
//    assertThat(exception.getMessage()).contains("[xyz]");
//    assertThat(exception.getMessage()).contains("[en, de]");
//  }
// }
