/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static java.util.Map.entry;

import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.SchemaManager;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.operate.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.JobTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.index.FormIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.tasklist.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.tasklist.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.GroupIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.MappingIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.PersistentWebSessionIndexDescriptor;
import io.camunda.webapps.schema.descriptors.usermanagement.index.RoleIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.TenantIndex;
import io.camunda.webapps.schema.descriptors.usermanagement.index.UserIndex;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootConfiguration
@EnableConfigurationProperties
public class StandaloneSchemaManager {

  private static final Logger LOG = LoggerFactory.getLogger(SchemaManager.class);

  /**
   * Copied from DefaultExporterResourceProvider until we extracted the methods there (probably just
   * exposed as static create methods)
   */
  private static Map<? extends Class<? extends AbstractIndexDescriptor>, IndexDescriptor>
      indexDescriptorsMap;

  private static Map<Class<? extends IndexTemplateDescriptor>, IndexTemplateDescriptor>
      templateDescriptorsMap;

  /**
   * Copied from DefaultExporterResourceProvider until we extracted the methods there (probably just
   * exposed as static create methods)
   */
  private static void setupMaps() {
    final String globalPrefix = "";
    final boolean isElasticsearch = true;

    templateDescriptorsMap =
        Map.ofEntries(
            entry(ListViewTemplate.class, new ListViewTemplate(globalPrefix, isElasticsearch)),
            entry(VariableTemplate.class, new VariableTemplate(globalPrefix, isElasticsearch)),
            entry(
                PostImporterQueueTemplate.class,
                new PostImporterQueueTemplate(globalPrefix, isElasticsearch)),
            entry(
                FlowNodeInstanceTemplate.class,
                new FlowNodeInstanceTemplate(globalPrefix, isElasticsearch)),
            entry(IncidentTemplate.class, new IncidentTemplate(globalPrefix, isElasticsearch)),
            entry(
                SequenceFlowTemplate.class,
                new SequenceFlowTemplate(globalPrefix, isElasticsearch)),
            entry(
                DecisionInstanceTemplate.class,
                new DecisionInstanceTemplate(globalPrefix, isElasticsearch)),
            entry(EventTemplate.class, new EventTemplate(globalPrefix, isElasticsearch)),
            entry(TaskTemplate.class, new TaskTemplate(globalPrefix, isElasticsearch)),
            entry(OperationTemplate.class, new OperationTemplate(globalPrefix, isElasticsearch)),
            entry(
                BatchOperationTemplate.class,
                new BatchOperationTemplate(globalPrefix, isElasticsearch)),
            entry(
                DraftTaskVariableTemplate.class,
                new DraftTaskVariableTemplate(globalPrefix, isElasticsearch)),
            entry(
                SnapshotTaskVariableTemplate.class,
                new SnapshotTaskVariableTemplate(globalPrefix, isElasticsearch)),
            entry(JobTemplate.class, new JobTemplate(globalPrefix, isElasticsearch)));

    indexDescriptorsMap =
        Map.ofEntries(
            entry(DecisionIndex.class, new DecisionIndex(globalPrefix, isElasticsearch)),
            entry(
                DecisionRequirementsIndex.class,
                new DecisionRequirementsIndex(globalPrefix, isElasticsearch)),
            entry(MetricIndex.class, new MetricIndex(globalPrefix, isElasticsearch)),
            entry(ProcessIndex.class, new ProcessIndex(globalPrefix, isElasticsearch)),
            entry(FormIndex.class, new FormIndex(globalPrefix, isElasticsearch)),
            entry(
                TasklistMetricIndex.class, new TasklistMetricIndex(globalPrefix, isElasticsearch)),
            entry(RoleIndex.class, new RoleIndex(globalPrefix, isElasticsearch)),
            entry(UserIndex.class, new UserIndex(globalPrefix, isElasticsearch)),
            entry(AuthorizationIndex.class, new AuthorizationIndex(globalPrefix, isElasticsearch)),
            entry(MappingIndex.class, new MappingIndex(globalPrefix, isElasticsearch)),
            entry(TenantIndex.class, new TenantIndex(globalPrefix, isElasticsearch)),
            entry(GroupIndex.class, new GroupIndex(globalPrefix, isElasticsearch)),
            entry(
                ImportPositionIndex.class, new ImportPositionIndex(globalPrefix, isElasticsearch)),
            entry(
                TasklistImportPositionIndex.class,
                new TasklistImportPositionIndex(globalPrefix, isElasticsearch)),
            entry(
                PersistentWebSessionIndexDescriptor.class,
                new PersistentWebSessionIndexDescriptor(globalPrefix, isElasticsearch)));
  }

  public static void main(final String[] args) {

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

    final SpringApplication standaloneSchemaManagerApplication =
        new SpringApplicationBuilder()
            .web(WebApplicationType.NONE)
            .logStartupInfo(true)
            .sources(StandaloneSchemaManager.class, SchemaManagerConnectConfiguration.class)
            .addCommandLineProperties(true)
            .listeners(new ApplicationErrorListener())
            .build(args);

    setupMaps();

    final ConfigurableApplicationContext applicationContext =
        standaloneSchemaManagerApplication.run(args);

    final SchemaManagerConnectConfiguration connectConfiguration =
        applicationContext.getBean(SchemaManagerConnectConfiguration.class);

    LOG.info("Creating/updating Elasticsearch schema for Camunda ...");

    final ExporterConfiguration exporterConfig = new ExporterConfiguration();
    exporterConfig.setConnect(connectConfiguration);

    final SearchEngineClient client = ClientAdapter.of(exporterConfig).getSearchEngineClient();
    final SchemaManager schemaManager =
        new SchemaManager(
            client, indexDescriptorsMap.values(), templateDescriptorsMap.values(), exporterConfig);

    schemaManager.startup();

    LOG.info("... finished creating/updating Elasticsearch schema for Camunda");
    System.exit(0);
  }

  @ConfigurationProperties("camunda.elasticsearch")
  public static final class SchemaManagerConnectConfiguration extends ConnectConfiguration {}
}
