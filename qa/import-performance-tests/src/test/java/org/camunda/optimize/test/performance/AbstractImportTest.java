/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.performance;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public abstract class AbstractImportTest {
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final Properties properties = getProperties();

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension =
    new ElasticSearchIntegrationTestExtension();
  @RegisterExtension
  @Order(2)
  public static EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();
  @RegisterExtension
  @Order(3)
  public EngineDatabaseExtension engineDatabaseExtension = new EngineDatabaseExtension(properties);

  protected long maxImportDurationInMin;
  protected ConfigurationService configurationService;

  abstract Properties getProperties();

  @BeforeEach
  public void setUp() {
    maxImportDurationInMin = Long.parseLong(properties.getProperty("import.test.max.duration.in.min", "240"));
    elasticSearchIntegrationTestExtension.disableCleanup();
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    configurationService.getCleanupServiceConfiguration().getProcessDataCleanupConfiguration().setEnabled(false);
  }

  protected void logStats() {
    try {
      logger.info(
        "The Camunda Platform contains {} process definitions. Optimize: {}",
        (engineDatabaseExtension.countProcessDefinitions()),
        elasticSearchIntegrationTestExtension.getDocumentCountOf(PROCESS_DEFINITION_INDEX_NAME)
      );
      logger.info(
        "The Camunda Platform contains {} historic process instances. Optimize: {}",
        engineDatabaseExtension.countHistoricProcessInstances(),
        elasticSearchIntegrationTestExtension.getDocumentCountOf(PROCESS_INSTANCE_MULTI_ALIAS)
      );
      logger.info(
        "The Camunda Platform contains {} historic variable instances. Optimize: {}",
        engineDatabaseExtension.countHistoricVariableInstances(),
        elasticSearchIntegrationTestExtension.getVariableInstanceCount()
      );
      logger.info(
        "The Camunda Platform contains {} historic activity instances. Optimize: {}",
        engineDatabaseExtension.countHistoricActivityInstances(),
        elasticSearchIntegrationTestExtension.getActivityCount()
      );

      logger.info(
        "The Camunda Platform contains {} decision definitions. Optimize: {}",
        engineDatabaseExtension.countDecisionDefinitions(),
        elasticSearchIntegrationTestExtension.getDocumentCountOf(DECISION_DEFINITION_INDEX_NAME)
      );
      logger.info(
        "The Camunda Platform contains {} historic decision instances. Optimize: {}",
        engineDatabaseExtension.countHistoricDecisionInstances(),
        elasticSearchIntegrationTestExtension.getDocumentCountOf(DECISION_INSTANCE_MULTI_ALIAS)
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
    Integer processInstancesImported =
      elasticSearchIntegrationTestExtension.getDocumentCountOf(PROCESS_INSTANCE_MULTI_ALIAS);
    Long totalInstances;
    try {
      totalInstances = Math.max(engineDatabaseExtension.countHistoricProcessInstances(), 1L);
      return Math.round(processInstancesImported.doubleValue() / totalInstances.doubleValue() * 100);
    } catch (SQLException e) {
      e.printStackTrace();
      return 0L;
    }
  }

  protected void assertThatEngineAndElasticDataMatch() throws SQLException {
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(PROCESS_DEFINITION_INDEX_NAME))
      .as("processDefinitionsCount").isEqualTo(engineDatabaseExtension.countProcessDefinitions());
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(PROCESS_INSTANCE_MULTI_ALIAS))
      .as("processInstanceTypeCount").isEqualTo(engineDatabaseExtension.countHistoricProcessInstances());
    assertThat(elasticSearchIntegrationTestExtension.getVariableInstanceCount())
      .as("variableInstanceCount").isGreaterThanOrEqualTo(engineDatabaseExtension.countHistoricVariableInstances());
    assertThat(elasticSearchIntegrationTestExtension.getActivityCount())
      .as("historicActivityInstanceCount").isEqualTo(engineDatabaseExtension.countHistoricActivityInstances());
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(DECISION_DEFINITION_INDEX_NAME))
      .as("decisionDefinitionsCount").isEqualTo(engineDatabaseExtension.countDecisionDefinitions());
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(DECISION_INSTANCE_MULTI_ALIAS))
      .as("decisionInstancesCount").isEqualTo(engineDatabaseExtension.countHistoricDecisionInstances());
  }
}
