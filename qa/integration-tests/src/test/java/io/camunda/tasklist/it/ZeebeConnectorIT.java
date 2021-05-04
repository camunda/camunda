/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.management.HealthCheckTest.AddManagementPropertiesInitializer;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.ElasticsearchTestRule;
import io.camunda.tasklist.util.EmbeddedZeebeConfigurer;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TasklistZeebeRule;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.util.ZeebeClientRule;
import io.camunda.tasklist.zeebe.PartitionHolder;
import io.camunda.tasklist.zeebeimport.ZeebeImporter;
import org.apache.http.HttpStatus;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + ".zeebe.gatewayAddress = localhost:55500"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = AddManagementPropertiesInitializer.class)
public class ZeebeConnectorIT extends TasklistIntegrationTest {

  @Rule public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired private ZeebeImporter zeebeImporter;

  @Autowired private PartitionHolder partitionHolder;

  @Autowired private EmbeddedZeebeConfigurer embeddedZeebeConfigurer;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired private TestRestTemplate testRestTemplate;

  private TasklistZeebeRule tasklistZeebeRule;

  private ZeebeClientRule clientRule;

  @After
  public void cleanup() {
    if (tasklistZeebeRule != null) {
      tasklistZeebeRule.finished(null);
    }
    if (clientRule != null) {
      clientRule.after();
    }
  }

  @Test
  public void testZeebeConnection() throws Exception {
    // when 1
    // no Zeebe broker is running

    // then 1
    // application context must be successfully started
    testRequest("/actuator/health/liveness");
    // import is working fine
    zeebeImporter.performOneRoundOfImport();
    // partition list is empty
    assertThat(partitionHolder.getPartitionIds()).isEmpty();

    // when 2
    // Zeebe is started
    startZeebe();

    // then 2
    // data import is working
    zeebeImporter.performOneRoundOfImport();
    // partition list is not empty
    assertThat(partitionHolder.getPartitionIds()).isNotEmpty();
  }

  private void testRequest(String url) {
    final ResponseEntity<Object> entity =
        testRestTemplate.exchange(url, HttpMethod.GET, null, Object.class);
    assertThat(entity.getStatusCode().value()).isEqualTo(HttpStatus.SC_OK);
  }

  private void startZeebe() {
    tasklistZeebeRule = new TasklistZeebeRule();
    tasklistZeebeRule.setEmbeddedZeebeConfigurer(embeddedZeebeConfigurer);
    tasklistZeebeRule.setTasklistProperties(tasklistProperties);
    tasklistZeebeRule.setZeebeEsClient(zeebeEsClient);
    clientRule = new ZeebeClientRule(tasklistZeebeRule.getBrokerRule());
    tasklistZeebeRule.starting(null);
    clientRule.before();
    tasklistProperties.getZeebeElasticsearch().setPrefix(tasklistZeebeRule.getPrefix());
    partitionHolder.setZeebeClient(clientRule.getClient());
  }

  @Test
  public void testRecoverAfterZeebeRestart() throws Exception {
    // when 1
    // Zeebe is started
    startZeebe();

    // then 1
    // data import is working
    zeebeImporter.performOneRoundOfImport();

    // when 2
    // Zeebe is restarted
    tasklistZeebeRule.finished(null);
    clientRule.after();
    tasklistZeebeRule.starting(null);
    clientRule.before();

    // then 2
    // data import is still working
    zeebeImporter.performOneRoundOfImport();
  }
}
