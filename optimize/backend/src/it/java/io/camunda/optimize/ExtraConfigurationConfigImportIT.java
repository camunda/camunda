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
}
