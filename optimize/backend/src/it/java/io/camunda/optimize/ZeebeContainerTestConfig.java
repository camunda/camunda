/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_ELASTICSEARCH_EXPORTER;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_OPENSEARCH_EXPORTER;

import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import io.zeebe.containers.ZeebeContainer;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Configuration
public class ZeebeContainerTestConfig {

  private static final String ZEEBE_CONFIG_PATH = "zeebe/zeebe-application.yml";
  private static final String ZEEBE_VERSION =
      IntegrationTestConfigurationUtil.getZeebeDockerVersion();

  @Bean
  public ZeebeContainer zeebeContainer() {
    ZeebeContainer zeebeContainer;
    final int databasePort;
    final String zeebeExporterClassName;
    if (IntegrationTestConfigurationUtil.getDatabaseType().equals(DatabaseType.OPENSEARCH)) {
      databasePort = 9205;
      zeebeExporterClassName = ZEEBE_OPENSEARCH_EXPORTER;
    } else {
      databasePort = 9200;
      zeebeExporterClassName = ZEEBE_ELASTICSEARCH_EXPORTER;
    }
    Testcontainers.exposeHostPorts(databasePort);
    zeebeContainer =
        new ZeebeContainer(DockerImageName.parse("camunda/zeebe:" + ZEEBE_VERSION))
            .withEnv("ZEEBE_CLOCK_CONTROLLED", "true")
            .withEnv("DATABASE_PORT", String.valueOf(databasePort))
            .withEnv("ZEEBE_EXPORTER_CLASS_NAME", zeebeExporterClassName)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource(ZEEBE_CONFIG_PATH),
                "/usr/local/zeebe/config/application.yml");
    if (!isZeebeVersionPre85()) {
      zeebeContainer =
          zeebeContainer
              .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
              .withAdditionalExposedPort(8080);
    }
    return zeebeContainer;
  }

  public static boolean isZeebeVersionPre85() {
    final Pattern zeebeVersionPattern = Pattern.compile("8.0.*|8.1.*|8.2.*|8.3.*|8.4.*");
    return zeebeVersionPattern
        .matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion())
        .matches();
  }
}
