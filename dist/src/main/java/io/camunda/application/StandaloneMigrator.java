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
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.SequenceFlowDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
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
  private static final int BATCH_SIZE = 100000;
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
      migrateEntitiesWithBatchFlush(
          ProcessDefReader.readProcessDefinitions(client),
          rdbmsWriter.getProcessDefinitionWriter()::create,
          rdbmsWriter,
          ProcessDefinitionDbModel::processDefinitionKey);
      LOG.info("Process definitions migrated successfully.");

      LOG.info("Migrating process instances...");
      migrateEntitiesWithBatchFlush(
          ProcessInstanceReader.readProcessInstances(client),
          rdbmsWriter.getProcessInstanceWriter()::create,
          rdbmsWriter,
          ProcessInstanceDbModel::processInstanceKey);
      LOG.info("Process instances migrated successfully.");

      LOG.info("Migrating flow node instances...");
      migrateEntitiesWithBatchFlush(
          FlowNodeInstanceReader.readFlowNodeInstances(client),
          rdbmsWriter.getFlowNodeInstanceWriter()::create,
          rdbmsWriter,
          FlowNodeInstanceDbModel::flowNodeInstanceKey);
      LOG.info("Flow node instances migrated successfully.");

      LOG.info("Migrating variables...");
      // migrateEntitiesWithBatchFlush(
      //    VariableReader.readVariables(client),
      //    rdbmsWriter.getVariableWriter()::create,
      //    rdbmsWriter);
      rdbmsWriter.flush(true);
      LOG.info("Variables migrated successfully.");

      LOG.info("Migrating jobs...");
      migrateEntitiesWithBatchFlush(
          JobReader.readJobs(client),
          rdbmsWriter.getJobWriter()::create,
          rdbmsWriter,
          JobDbModel::jobKey);
      LOG.info("Jobs migrated successfully.");

      LOG.info("Migrating sequence flows...");
      migrateEntitiesWithBatchFlush(
          SequenceFlowReader.readSequenceFlows(client),
          rdbmsWriter.getSequenceFlowWriter()::create,
          rdbmsWriter,
          SequenceFlowDbModel::flowNodeId);
      LOG.info("Sequence flows migrated successfully.");

      LOG.info("Migrating message subscriptions...");
      migrateEntitiesWithBatchFlush(
          MessageSubscriptionReader.readMessageSubscriptions(client),
          rdbmsWriter.getMessageSubscriptionWriter()::create,
          rdbmsWriter,
          MessageSubscriptionDbModel::messageSubscriptionKey);
      LOG.info("Message subscriptions migrated successfully.");

      LOG.info("Migrating user tasks...");
      migrateEntitiesWithBatchFlush(
          UserTaskReader.readUserTasks(client),
          rdbmsWriter.getUserTaskWriter()::create,
          rdbmsWriter,
          UserTaskDbModel::userTaskKey);
      LOG.info("User tasks migrated successfully.");

      LOG.info("Migrating decision instances...");
      migrateEntitiesWithBatchFlush(
          DecisionInstanceReader.readDecisionInstances(client),
          rdbmsWriter.getDecisionInstanceWriter()::create,
          rdbmsWriter,
          DecisionInstanceDbModel::decisionInstanceId);
      LOG.info("Decision instances migrated successfully.");

      LOG.info("Migrating incidents...");
      migrateEntitiesWithBatchFlush(
          IncidentReader.readIncidents(client),
          rdbmsWriter.getIncidentWriter()::create,
          rdbmsWriter,
          IncidentDbModel::incidentKey);
      LOG.info("Incidents migrated successfully.");

    } catch (final Exception e) {
      LOG.error("Failed to migrate from ES to RDBMS", e);
      throw e;
    }
  }

  private <T, K> void migrateEntitiesWithBatchFlush(
      final java.util.List<T> entities,
      final java.util.function.Consumer<T> writer,
      final RdbmsWriters rdbmsWriter,
      final Function<T, K> keyExtractor) {
    int count = 0;
    final Set<K> keys = new HashSet<>();
    for (final T entity : entities) {
      if (keys.contains(keyExtractor.apply(entity))) {
        continue;
      } else {
        keys.add(keyExtractor.apply(entity));
      }
      writer.accept(entity);
      count++;
      if (count % BATCH_SIZE == 0) {
        rdbmsWriter.flush(true);
        LOG.info("Flushed {} entities", count);
      }
    }
    // Final flush for remaining entities
    if (count % BATCH_SIZE != 0) {
      rdbmsWriter.flush(true);
    }
    LOG.info("Total {} entities migrated", count);
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
