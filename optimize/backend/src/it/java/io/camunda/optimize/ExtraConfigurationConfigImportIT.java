/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

public class ExtraConfigurationConfigImportIT extends AbstractCCSMIT {

  @Test
  void shouldLoadYamlFromConfigDirViaSpringConfigImport() throws Exception {
    // given: ./config/additional-spring-properties.yaml
    final Path configDir = Path.of("config");
    Files.createDirectories(configDir);

    Files.writeString(
        configDir.resolve("additional-spring-properties.yaml"),
        """
        optimize:
          it:
            configImportMarker: "from-extra-config"
        """);

    // when: optimize is restarted
    startAndUseNewOptimizeInstance();

    // then: the marker from ./config/* is present in the Spring Environment
    final Environment env = embeddedOptimizeExtension.getBean(Environment.class);
    assertThat(env.getProperty("optimize.it.configImportMarker")).isEqualTo("from-extra-config");
  }
}
