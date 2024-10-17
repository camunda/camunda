/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.migration.util;

import static org.junit.Assume.assumeTrue;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.descriptors.operate.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@ContextConfiguration(
    classes = {
      TestConfig.class,
      ElasticsearchSchemaManager.class,
      RetryElasticsearchClient.class,
      ElasticsearchTaskStore.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      JacksonConfig.class
    })
@TestPropertySource(locations = "/test.properties")
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public abstract class AbstractMigrationTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMigrationTest.class);

  @Autowired protected EntityReader entityReader;

  @Autowired protected ProcessIndex processTemplate;

  @Autowired protected DecisionIndex decisionTemplate;

  @Autowired protected DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired protected ListViewTemplate listViewTemplate;

  @Autowired protected DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired protected EventTemplate eventTemplate;

  @Autowired protected SequenceFlowTemplate sequenceFlowTemplate;

  @Autowired protected FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired protected VariableTemplate variableTemplate;

  @Autowired protected IncidentTemplate incidentTemplate;

  @Autowired protected ImportPositionIndex importPositionIndex;

  @Autowired protected OperationTemplate operationTemplate;

  @Autowired protected PostImporterQueueTemplate postImporterQueueTemplate;

  @Autowired protected UserIndex userIndex;

  @Autowired protected MetricIndex metricIndex;

  @Autowired protected RestHighLevelClient esClient;

  @Autowired protected TestContext testContext;

  protected void assumeThatProcessIsUnderTest(final String bpmnProcessId) {
    assumeTrue(testContext.getProcessesToAssert().contains(bpmnProcessId));
  }

  @Configuration
  @ComponentScan(
      basePackages = {
        "io.camunda.operate.property",
        "io.camunda.operate.schema.indices",
        "io.camunda.operate.schema.templates",
        "io.camunda.operate.qa.migration",
        "io.camunda.operate.util.rest"
      },
      nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
  public static class SpringConfig {}
}
