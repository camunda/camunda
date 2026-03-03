/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;

public class ExtraConfigurationConfigImportIT extends AbstractCCSMIT {

  private Path configDir;

  @BeforeEach
  void setUp(@TempDir final Path tempDir) {
    // create empty config dir to be used in tests
    configDir = tempDir.resolve("config");
    try {
      Files.createDirectories(configDir);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to create config dir for test", e);
    }
  }

  @Override
  protected void startAndUseNewOptimizeInstance() {
    startAndUseNewOptimizeInstance(
        Map.of("spring.config.location", configDir.toAbsolutePath() + "/"),
        ConfigurationServiceConstants.CCSM_PROFILE);
  }

  @Test
  void shouldLoadYamlFromConfigDirViaSpringConfigImport() throws Exception {
    // given: ./config/additional-spring-properties.yaml
    Files.writeString(
        configDir.resolve("application.yaml"),
        """
        spring:
          config:
            import: "additional-spring-properties.yaml"
        """);

    Files.writeString(
        configDir.resolve("additional-spring-properties.yaml"),
        """
        optimize:
          it:
            configImportMarker: "from-extra-config"
        logging:
          level:
            io.camunda.optimize: DEBUG
        """);

    // when: optimize is restarted
    startAndUseNewOptimizeInstance();

    // then: the marker from ./config/* is present in the Spring Environment
    final Environment env = embeddedOptimizeExtension.getBean(Environment.class);
    assertThat(env.getProperty("optimize.it.configImportMarker")).isEqualTo("from-extra-config");
    assertThat(env.getProperty("logging.level.io.camunda.optimize")).isEqualTo("DEBUG");
  }

  @Test
  void shouldLoadOptimizeConfigFromExtraFile() throws Exception {
    // given: ./config/additional-spring-properties.yaml
    Files.writeString(
        configDir.resolve("application.yaml"),
        """
        spring:
          config:
            import: "additional-spring-properties.yaml"
        """);

    Files.writeString(
        configDir.resolve("additional-spring-properties.yaml"),
        """
        es:
          settings:
            index:
              number_of_replicas: 6
        logging:
          level:
            io.camunda.optimize: DEBUG
        """);

    // when: optimize is restarted
    startAndUseNewOptimizeInstance();

    // then: the custom optimize config is loaded and can be accessed via the ConfigurationService
    assertThat(
            embeddedOptimizeExtension
                .getConfigurationService()
                .getElasticSearchConfiguration()
                .getNumberOfReplicas())
        .isEqualTo(6);
  }

  /** Regression test for <a href="https://github.com/camunda/camunda/issues/47472">#47472</a> */
  @Test
  void shouldLoadEnvYamlImportedFromApplicationCcsmProfile() throws Exception {
    // given: a setup mimicking Kubernetes ConfigMap with application-ccsm.yaml importing files
    // where the import is comma-delimited with whitespaces
    Files.writeString(
        configDir.resolve("application-ccsm.yaml"),
        """
        spring:
          config:
            import: optional:file:%s/boat.yaml, optional:file:%s/about.yaml, optional:file:%s/env.yaml
        """
            .formatted(
                configDir.toAbsolutePath(),
                configDir.toAbsolutePath(),
                configDir.toAbsolutePath()));

    Files.writeString(
        configDir.resolve("about.yaml"),
        """
        logging:
          level:
            ROOT: DEBUG
            io.camunda.modeler: DEBUG
        """);

    Files.writeString(
        configDir.resolve("boat.yaml"),
        """
        logging:
          level:
            ROOT: INFO
            io.camunda.modeler: INFO
        """);

    Files.writeString(
        configDir.resolve("env.yaml"),
        """
        zeebe:
          enabled: true
          partitionCount: 3
          name: "zeebe-record"
        """);

    Files.writeString(
        configDir.resolve("environment-config.yaml"),
        """
        spring:
          servlet:
            multipart:
              max-file-size: "10MB"
              max-request-size: "10MB"
          profiles:
            active: "ccsm"
        """);

    // when: optimize is restarted
    startAndUseNewOptimizeInstance();

    // then: Optimize-specific config from env.yaml is accessible via ConfigurationService
    final var configService = embeddedOptimizeExtension.getConfigurationService();
    assertThat(configService.getConfiguredZeebe().getPartitionCount()).isEqualTo(3);
  }

  /** Regression test for <a href="https://github.com/camunda/camunda/issues/47473">#47473</a> */
  @Test
  void shouldLoadEnvYamlImportedAsList() throws Exception {
    // given: a setup mimicking Kubernetes ConfigMap with application-ccsm.yaml importing files
    // where the import is specified as a list
    Files.writeString(
        configDir.resolve("application-ccsm.yaml"),
        """
        spring:
          config:
            import:
              - optional:file:%s/boat.yaml
              - optional:file:%s/about.yaml
              - optional:file:%s/env.yaml
        """
            .formatted(
                configDir.toAbsolutePath(),
                configDir.toAbsolutePath(),
                configDir.toAbsolutePath()));

    Files.writeString(
        configDir.resolve("about.yaml"),
        """
        logging:
          level:
            ROOT: DEBUG
            io.camunda.modeler: DEBUG
        """);

    Files.writeString(
        configDir.resolve("boat.yaml"),
        """
        logging:
          level:
            ROOT: INFO
            io.camunda.modeler: INFO
        """);

    Files.writeString(
        configDir.resolve("env.yaml"),
        """
        zeebe:
          enabled: true
          partitionCount: 3
          name: "zeebe-record"
        """);

    Files.writeString(
        configDir.resolve("environment-config.yaml"),
        """
        spring:
          servlet:
            multipart:
              max-file-size: "10MB"
              max-request-size: "10MB"
          profiles:
            active: "ccsm"
        """);

    // when: optimize is restarted
    startAndUseNewOptimizeInstance();

    // then: Optimize-specific config from env.yaml is accessible via ConfigurationService
    final var configService = embeddedOptimizeExtension.getConfigurationService();
    assertThat(configService.getConfiguredZeebe().getPartitionCount()).isEqualTo(3);
  }
}
