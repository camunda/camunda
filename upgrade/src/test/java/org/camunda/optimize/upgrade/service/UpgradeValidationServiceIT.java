/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.createEmptyEnvConfig;
import static org.camunda.optimize.upgrade.EnvironmentConfigUtil.deleteEnvConfig;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class UpgradeValidationServiceIT extends AbstractUpgradeIT {

  private UpgradeValidationService underTest;

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    underTest = new UpgradeValidationService(new ElasticsearchMetadataService(new ObjectMapper()), prefixAwareClient);
    initSchema(Lists.newArrayList(METADATA_INDEX));
  }

  @Test
  public void versionValidationBreaksWithoutIndex() {
    try {
      underTest.validateSchemaVersions("2.0", "2.1");
    } catch (UpgradeRuntimeException e) {
      //expected
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void versionValidationBreaksWithoutMatchingVersion() {
    //given
    setMetadataIndexVersion("Test");

    try {
      //when
      underTest.validateSchemaVersions("2.0", "2.1");
    } catch (UpgradeRuntimeException e) {
      //expected
      //then
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void versionValidationPassesWithMatchingVersion() {
    //given
    setMetadataIndexVersion("2.0");

    //when
    underTest.validateSchemaVersions("2.0", "2.1");

    //then - no exception
  }

  @Test
  public void toVersionIsNotAllowedToBeNull() {
    //given
    setMetadataIndexVersion("2.0");

    try {
      //when
      underTest.validateSchemaVersions("2.0", null);
    } catch (UpgradeRuntimeException e) {
      //expected
      //then
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void toVersionIsNotAllowedToBeEmptyString() {
    //given
    setMetadataIndexVersion("2.0");

    try {
      //when
      underTest.validateSchemaVersions("2.0", "");
    } catch (UpgradeRuntimeException e) {
      //expected
      //then
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void validateThrowsExceptionWithoutEnvironmentConfig() throws Exception {
    // given
    deleteEnvConfig();

    // then throws exception
    RuntimeException exception =
      assertThrows(RuntimeException.class, () -> underTest.validateEnvironmentConfigInClasspath());
    assertThat(exception.getMessage(), is("Couldn't read environment-config.yaml from environment folder in Optimize root!"));
  }

  @Test
  public void validateWithEnvironmentConfig() throws Exception {
    //given
    createEmptyEnvConfig();

    //when
    underTest.validateEnvironmentConfigInClasspath();
  }
}