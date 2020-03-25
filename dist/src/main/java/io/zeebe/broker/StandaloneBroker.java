/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker;

import static java.lang.Runtime.getRuntime;

import io.zeebe.EnvironmentHelper;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.legacy.tomlconfig.LegacyConfigurationSupport;
import io.zeebe.legacy.tomlconfig.LegacyConfigurationSupport.Scope;
import io.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class StandaloneBroker implements CommandLineRunner {

  @Autowired BrokerCfg configuration;
  @Autowired Environment springEnvironment;

  private final CountDownLatch waiting_latch = new CountDownLatch(1);
  private String tempFolder;

  public static void main(final String[] args) throws Exception {
    System.setProperty("spring.banner.location", "classpath:/assets/zeebe_broker_banner.txt");

    final LegacyConfigurationSupport legacyConfigSupport =
        new LegacyConfigurationSupport(Scope.BROKER);
    legacyConfigSupport.checkForLegacyTomlConfigurationArgument(args, "broker.cfg.yaml");
    legacyConfigSupport.checkForLegacyEnvironmentVariables();

    SpringApplication.run(StandaloneBroker.class, args);
  }

  @Override
  public void run(final String... args) throws Exception {
    final Broker broker;

    if (EnvironmentHelper.isProductionEnvironment(springEnvironment)) {
      broker = createBrokerInBaseDirectory();
    } else {
      broker = createBrokerInTempDirectory();
    }

    broker.start();
    getRuntime()
        .addShutdownHook(
            new Thread("Broker close Thread") {
              @Override
              public void run() {
                try {
                  broker.close();
                } finally {
                  deleteTempDirectory();
                  LogManager.shutdown();
                }
              }
            });
    waiting_latch.await();
  }

  private Broker createBrokerInBaseDirectory() {
    String basePath = System.getProperty("basedir");

    if (basePath == null) {
      basePath = Paths.get(".").toAbsolutePath().normalize().toString();
    }

    return new Broker(configuration, basePath, null);
  }

  private Broker createBrokerInTempDirectory() {
    Loggers.SYSTEM_LOGGER.info("Launching broker in temporary folder.");

    try {
      tempFolder = Files.createTempDirectory("zeebe").toAbsolutePath().normalize().toString();
      return new Broker(configuration, tempFolder, null);
    } catch (final IOException e) {
      throw new RuntimeException("Could not start broker", e);
    }
  }

  private void deleteTempDirectory() {
    if (tempFolder != null) {
      try {
        FileUtil.deleteFolder(tempFolder);
      } catch (final IOException e) {
        e.printStackTrace();
      }
    }
  }
}
