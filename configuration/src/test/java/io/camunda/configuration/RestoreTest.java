/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.RestorePropertiesOverride;
import io.camunda.configuration.beans.RestoreProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  RestorePropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("restore")
public class RestoreTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.system.restore.validate-config=false",
        "camunda.system.restore.ignore-files-in-target=file1,file2,file3"
      })
  class WithOnlyUnifiedConfigSet {
    final RestoreProperties restoreCfg;

    WithOnlyUnifiedConfigSet(@Autowired final RestoreProperties restoreCfg) {
      this.restoreCfg = restoreCfg;
    }

    @Test
    void shouldSetValidateConfig() {
      assertThat(restoreCfg.validateConfig()).isFalse();
    }

    @Test
    void shouldSetIgnoreFilesInTarget() {
      assertThat(restoreCfg.ignoreFilesInTarget()).containsExactly("file1", "file2", "file3");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.restore.validateConfig=false",
        "zeebe.restore.ignoreFilesInTarget=legacyFile1,legacyFile2"
      })
  class WithOnlyLegacySet {
    final RestoreProperties restoreCfg;

    WithOnlyLegacySet(@Autowired final RestoreProperties restoreCfg) {
      this.restoreCfg = restoreCfg;
    }

    @Test
    void shouldSetValidateConfig() {
      assertThat(restoreCfg.validateConfig()).isFalse();
    }

    @Test
    void shouldSetIgnoreFilesInTarget() {
      assertThat(restoreCfg.ignoreFilesInTarget()).containsExactly("legacyFile1", "legacyFile2");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.system.restore.validate-config=false",
        "camunda.system.restore.ignore-files-in-target=newFile1,newFile2",
        // legacy
        "zeebe.restore.validateConfig=true",
        "zeebe.restore.ignoreFilesInTarget=legacyFile1,legacyFile2"
      })
  class WithNewAndLegacySet {
    final RestoreProperties restoreCfg;

    WithNewAndLegacySet(@Autowired final RestoreProperties restoreCfg) {
      this.restoreCfg = restoreCfg;
    }

    @Test
    void shouldSetValidateConfigFromNew() {
      assertThat(restoreCfg.validateConfig()).isFalse();
    }

    @Test
    void shouldSetIgnoreFilesInTargetFromNew() {
      assertThat(restoreCfg.ignoreFilesInTarget()).containsExactly("newFile1", "newFile2");
    }
  }
}
