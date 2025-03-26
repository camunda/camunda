/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.exporter;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_ENV_VAR;

import io.camunda.zeebe.it.exporter.util.TestExporter;
import io.camunda.zeebe.it.exporter.util.TestExporterConfig;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.nio.file.Path;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * The following needs to be a container test because it's the only way to test configuration via
 * environment variables in an isolated way at the moment.
 */
@Testcontainers
final class ExporterConfigInstantiationIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExporterConfigInstantiationIT.class);

  @RegressionTest("https://github.com/camunda/camunda/issues/4552")
  void shouldDeserializeListConfiguration(final @TempDir Path tempDir) throws IOException {
    // given
    final var exporterJar =
        new ByteBuddy()
            .rebase(TestExporter.class)
            .name("com.acme.Exporter")
            .make()
            .include(new ByteBuddy().rebase(TestExporterConfig.class).make())
            .toJar(tempDir.resolve("exporter.jar").toFile())
            .toPath();
    final var broker =
        new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withCopyFileToContainer(MountableFile.forHostPath(exporterJar), "/exporter.jar")
            .withEnv("ZEEBE_BROKER_EXPORTERS_TEST_CLASSNAME", "com.acme.Exporter")
            .withEnv("ZEEBE_BROKER_EXPORTERS_TEST_JARPATH", "/exporter.jar")
            .withEnv("ZEEBE_BROKER_EXPORTERS_TEST_ARGS_STRINGS_0", "foo")
            .withEnv(CREATE_SCHEMA_ENV_VAR, "false")
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));

    // when - then - the broker will fail to start if the strings were not properly parsed, as per
    // the TestExporter validation in its configure method
    try (broker) {
      broker.start();
    }
  }
}
