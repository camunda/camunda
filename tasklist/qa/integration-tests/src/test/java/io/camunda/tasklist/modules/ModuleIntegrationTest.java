/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.modules;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.management.HealthCheckIT.AddManagementPropertiesInitializer;
import io.camunda.tasklist.management.SearchEngineHealthIndicator;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestElasticsearchSchemaManager;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.util.apps.modules.ModulesTestApplication;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      ModulesTestApplication.class,
      TestElasticsearchSchemaManager.class,
      TestApplication.class,
      SearchEngineHealthIndicator.class,
      WebSecurityConfig.class,
      OAuth2WebConfigurer.class,
      RetryElasticsearchClient.class,
    },
    properties = {
      TasklistProperties.PREFIX + ".elasticsearch.createSchema = false",
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "graphql.servlet.websocket.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = AddManagementPropertiesInitializer.class)
public abstract class ModuleIntegrationTest {

  @Autowired protected ApplicationContext applicationContext;
}
