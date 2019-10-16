/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.performance;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtensionRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;

import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ContextConfiguration(locations = {"/import-applicationContext.xml"})
public abstract class AbstractImportTest {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final Properties properties = getProperties();

  @RegisterExtension
  @Order(1)
  protected ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  protected EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();
  @RegisterExtension
  @Order(3)
  protected EngineDatabaseExtensionRule engineDatabaseExtensionRule = new EngineDatabaseExtensionRule(properties);

  protected long maxImportDurationInMin;
  protected ConfigurationService configurationService;

  abstract Properties getProperties();

  @BeforeEach
  public void setUp() {
    maxImportDurationInMin = Long.parseLong(properties.getProperty("import.test.max.duration.in.min", "240"));
    elasticSearchIntegrationTestExtensionRule.disableCleanup();
    configurationService = embeddedOptimizeExtensionRule.getConfigurationService();
    configurationService.getCleanupServiceConfiguration().setEnabled(false);
  }

  protected void logStats() {
    try {
      logger.info(
        "The Camunda Platform contains {} process definitions. Optimize: {}",
        (engineDatabaseExtensionRule.countProcessDefinitions()),
        elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_DEFINITION_INDEX_NAME)
      );
      logger.info(
        "The Camunda Platform contains {} historic process instances. Optimize: {}",
        engineDatabaseExtensionRule.countHistoricProcessInstances(),
        elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_INSTANCE_INDEX_NAME)
      );
      logger.info(
        "The Camunda Platform contains {} historic variable instances. Optimize: {}",
        engineDatabaseExtensionRule.countHistoricVariableInstances(),
        elasticSearchIntegrationTestExtensionRule.getVariableInstanceCount()
      );
      logger.info(
        "The Camunda Platform contains {} historic activity instances. Optimize: {}",
        engineDatabaseExtensionRule.countHistoricActivityInstances(),
        elasticSearchIntegrationTestExtensionRule.getActivityCount()
      );

      logger.info(
        "The Camunda Platform contains {} decision definitions. Optimize: {}",
        engineDatabaseExtensionRule.countDecisionDefinitions(),
        elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(DECISION_DEFINITION_INDEX_NAME)
      );
      logger.info(
        "The Camunda Platform contains {} historic decision instances. Optimize: {}",
        engineDatabaseExtensionRule.countHistoricDecisionInstances(),
        elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(DECISION_INSTANCE_INDEX_NAME)
      );
    } catch (SQLException e) {
      logger.error("Failed producing stats", e);
    }
  }

  protected ScheduledExecutorService reportImportProgress() {
    final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat(getClass().getSimpleName()).build()
    );
    exec.scheduleAtFixedRate(
      () -> logger.info("Progress of engine import: {}%", computeImportProgress()),
      0,
      5,
      TimeUnit.SECONDS
    );
    return exec;
  }

  private long computeImportProgress() {
    // assumption: we know how many process instances have been generated
    Integer processInstancesImported = elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(
      PROCESS_INSTANCE_INDEX_NAME
    );
    Long totalInstances;
    try {
      totalInstances = Math.max(engineDatabaseExtensionRule.countHistoricProcessInstances(), 1L);
      return Math.round(processInstancesImported.doubleValue() / totalInstances.doubleValue() * 100);
    } catch (SQLException e) {
      e.printStackTrace();
      return 0L;
    }
  }

  protected void assertThatEngineAndElasticDataMatch() throws SQLException {
    assertThat(
      "processDefinitionsCount",
      elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(
        PROCESS_DEFINITION_INDEX_NAME
      ),
      is(engineDatabaseExtensionRule.countProcessDefinitions())
    );
    assertThat(
      "processInstanceTypeCount",
      elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_INSTANCE_INDEX_NAME),
      is(engineDatabaseExtensionRule.countHistoricProcessInstances())
    );
    assertThat(
      "variableInstanceCount",
      elasticSearchIntegrationTestExtensionRule.getVariableInstanceCount(),
      is(engineDatabaseExtensionRule.countHistoricVariableInstances())
    );
    assertThat(
      "historicActivityInstanceCount",
      elasticSearchIntegrationTestExtensionRule.getActivityCount(),
      is(engineDatabaseExtensionRule.countHistoricActivityInstances())
    );

    assertThat(
      "decisionDefinitionsCount",
      elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(DECISION_DEFINITION_INDEX_NAME),
      is(engineDatabaseExtensionRule.countDecisionDefinitions())
    );
    assertThat(
      "decisionInstancesCount",
      elasticSearchIntegrationTestExtensionRule.getDocumentCountOf(DECISION_INSTANCE_INDEX_NAME),
      is(engineDatabaseExtensionRule.countHistoricDecisionInstances())
    );
  }
}
