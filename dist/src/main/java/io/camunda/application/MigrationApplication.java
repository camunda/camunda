/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.commons.service.CamundaServicesConfiguration;
import io.camunda.application.commons.service.ServiceSecurityConfiguration;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.Migrator;
import io.camunda.zeebe.gateway.GatewayModuleConfiguration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@SpringBootApplication
@Profile({"migration", "identity-migration | process-migration"})
public class MigrationApplication implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(MigrationApplication.class);

  @Autowired private List<Migrator> migrators;

  public static void main(final String[] args) {
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/camunda_banner.txt");
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");

    final SpringApplication application =
        MainSupport.createDefaultApplicationBuilder()
            .sources(MigrationApplication.class)
            .headless(true)
            .addCommandLineProperties(true)
            .listeners(new ApplicationErrorListener())
            .build(args);

    application.addInitializers(new ProfileInitializer());

    application.run(args);
  }

  @Override
  public void run(final ApplicationArguments args) {
    final CountDownLatch latch = new CountDownLatch(migrators.size());
    try (final ThreadPoolExecutor executor =
        (ThreadPoolExecutor) Executors.newFixedThreadPool(migrators.size())) {
      migrators.forEach(
          migrator ->
              executor.submit(
                  () -> {
                    try {
                      migrator.run(args);
                    } catch (final MigrationException ex) {
                      LOG.error(ex.getMessage());
                    } finally {
                      latch.countDown();
                    }
                  }));
      latch.await();
    } catch (final InterruptedException e) {
      LOG.error("Migration failed", e);
      System.exit(1);
    }
    LOG.info("Migration finished, shutting down");
    System.exit(0);
  }

  @Configuration(proxyBeanMethods = false)
  @ComponentScan(basePackages = {"io.camunda.migration.identity"})
  @Profile("identity-migration")
  @Import({
    CommonsModuleConfiguration.class,
    GatewayModuleConfiguration.class,
    ServiceSecurityConfiguration.class,
    CamundaServicesConfiguration.class
  })
  public static class IdentityMigrationModuleConfiguration {}

  @Configuration(proxyBeanMethods = false)
  @ComponentScan(basePackages = {"io.camunda.migration.process"})
  @Profile("process-migration")
  @Import({
    CommonsModuleConfiguration.class,
    GatewayModuleConfiguration.class,
    ServiceSecurityConfiguration.class,
    CamundaServicesConfiguration.class
  })
  public static class ProcessMigrationModuleConfiguration {}

  private static final class ProfileInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {
      final Environment env = applicationContext.getEnvironment();
      if (env.acceptsProfiles(Profiles.of(io.camunda.application.Profile.MIGRATION.getId()))) {
        applicationContext.getEnvironment().addActiveProfile("process-migration");
        applicationContext.getEnvironment().addActiveProfile("identity-migration");
      } else if (!env.acceptsProfiles(Profiles.of("process-migration", "identity-migration"))) {
        throw new IllegalStateException("Incorrect profile configuration");
      }
    }
  }
}
