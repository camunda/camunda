/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Rdbms;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.db.rdbms.write.RdbmsWriterFactory;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneMigrator implements CommandLineRunner {

  private static final Logger LOG = LoggerFactory.getLogger(StandaloneMigrator.class);
  private final ConnectConfiguration elasticsearch;
  private final Rdbms rdbms;
  private final RdbmsWriterFactory rdbmsWriterFactory;

  public StandaloneMigrator(
      final ConnectConfiguration elasticsearch,
      final Rdbms rdbms,
      final RdbmsWriterFactory rdbmsWriterFactory) {
    this.elasticsearch = elasticsearch;
    this.rdbms = rdbms;
    this.rdbmsWriterFactory = rdbmsWriterFactory;
  }

  public static void main(final String[] args) throws IOException {

    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    // Workaround for https://github.com/spring-projects/spring-boot/issues/26627
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");

    // show banner
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/camunda_banner.txt");

    LOG.info("Creating/updating schema for Camunda ...");

    MainSupport.createDefaultApplicationBuilder()
        .web(WebApplicationType.NONE)
        .logStartupInfo(true)
        .sources(Configuration.class, StandaloneMigrator.class, RdbmsConfiguration.class)
        .addCommandLineProperties(true)
        .properties("camunda.data.secondary-storage.type=rdbms")
        .profiles("migrator")
        .listeners(new ApplicationErrorListener())
        .run(args);

    LOG.info("... finished migrating from ES to RDBMS.");

    // Explicit exit needed because there are daemon threads (at least from the ES client) that are
    // blocking shutdown.
    System.exit(0);
  }

  @Override
  public void run(final String... args) throws Exception {
    try {
      LOG.info("Starting migration from ES to RDBMS ...");
      final ElasticsearchClient client = new ElasticsearchConnector(elasticsearch).createClient();
      final var rdbmsWriter =
          rdbmsWriterFactory.createWriter(new RdbmsWriterConfig.Builder().build());

      LOG.info("Migrating process definitions...");
      ProcessDefReader.readProcessDefinitions(client).stream()
          .forEach(rdbmsWriter.getProcessDefinitionWriter()::create);
      rdbmsWriter.flush(true);
      LOG.info("Process definitions migrated successfully.");

      LOG.info("Migrating process instances...");
      ProcessInstanceReader.readProcessInstances(client).stream()
          .forEach(rdbmsWriter.getProcessInstanceWriter()::create);
      rdbmsWriter.flush(true);
      LOG.info("Process instances migrated successfully.");

      LOG.info("Migrating flow node instances...");
      FlowNodeInstanceReader.readFlowNodeInstances(client).stream()
          .forEach(rdbmsWriter.getFlowNodeInstanceWriter()::create);
      rdbmsWriter.flush(true);
      LOG.info("Flow node instances migrated successfully.");

      LOG.info("Migrating variables...");
      VariableReader.readVariables(client).stream()
          .forEach(rdbmsWriter.getVariableWriter()::create);
      rdbmsWriter.flush(true);
      LOG.info("Variables migrated successfully.");

      LOG.info("Migrating jobs...");
      JobReader.readJobs(client).stream().forEach(rdbmsWriter.getJobWriter()::create);
      rdbmsWriter.flush(true);
      LOG.info("Jobs migrated successfully.");

      LOG.info("Migrating sequence flows...");
      SequenceFlowReader.readSequenceFlows(client).stream()
          .forEach(rdbmsWriter.getSequenceFlowWriter()::create);
      rdbmsWriter.flush(true);
      LOG.info("Sequence flows migrated successfully.");

      LOG.info("Migrating message subscriptions...");
      MessageSubscriptionReader.readMessageSubscriptions(client).stream()
          .forEach(rdbmsWriter.getMessageSubscriptionWriter()::create);
      rdbmsWriter.flush(true);
      LOG.info("Message subscriptions migrated successfully.");

      LOG.info("Migrating user tasks...");
      UserTaskReader.readUserTasks(client).stream()
          .forEach(rdbmsWriter.getUserTaskWriter()::create);
      rdbmsWriter.flush(true);
      LOG.info("User tasks migrated successfully.");

      LOG.info("Migrating decision instances...");
      DecisionInstanceReader.readDecisionInstances(client).stream()
          .forEach(rdbmsWriter.getDecisionInstanceWriter()::create);
      rdbmsWriter.flush(true);
      LOG.info("Decision instances migrated successfully.");

      LOG.info("Migrating incidents...");
      IncidentReader.readIncidents(client).stream()
          .forEach(rdbmsWriter.getIncidentWriter()::create);
      rdbmsWriter.flush(true);
      LOG.info("Incidents migrated successfully.");

    } catch (final Exception e) {
      LOG.error("Failed to migrate from ES to RDBMS", e);
      throw e;
    }
  }

  @EnableConfigurationProperties({ElasticsearchProperties.class, RdbmsProperties.class})
  public static class Configuration {
    @Bean
    public MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    public Camunda camunda() {
      return new Camunda();
    }
  }

  @ConfigurationProperties("camunda.migration.es")
  public static class ElasticsearchProperties extends ConnectConfiguration {}

  @ConfigurationProperties("camunda.migration.rdbms")
  public static class RdbmsProperties extends Rdbms {}
}
