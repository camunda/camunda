/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.util.ElasticsearchTestRule;
import io.zeebe.tasklist.util.EmbeddedZeebeConfigurer;
import io.zeebe.tasklist.util.TasklistIntegrationTest;
import io.zeebe.tasklist.util.TasklistZeebeRule;
import io.zeebe.tasklist.util.TestApplication;
import io.zeebe.tasklist.util.ZeebeClientRule;
import io.zeebe.tasklist.webapp.rest.HealthCheckRestService;
import io.zeebe.tasklist.zeebe.PartitionHolder;
import io.zeebe.tasklist.zeebeimport.ZeebeImporter;
import org.apache.http.HttpStatus;
import org.assertj.core.api.Assertions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + ".zeebe.brokerContactPoint = localhost:55500"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    testRequest(HealthCheckRestService.HEALTH_CHECK_URL);
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
    try {
      FieldSetter.setField(
          tasklistZeebeRule,
          TasklistZeebeRule.class.getDeclaredField("tasklistProperties"),
          tasklistProperties);
      FieldSetter.setField(
          tasklistZeebeRule,
          TasklistZeebeRule.class.getDeclaredField("zeebeEsClient"),
          zeebeEsClient);
      FieldSetter.setField(
          tasklistZeebeRule,
          TasklistZeebeRule.class.getDeclaredField("embeddedZeebeConfigurer"),
          embeddedZeebeConfigurer);
    } catch (NoSuchFieldException e) {
      Assertions.fail("Failed to inject fields in tasklistZeebeRule");
    }
    clientRule = new ZeebeClientRule(tasklistZeebeRule.getBrokerRule());
    tasklistZeebeRule.starting(null);
    clientRule.before();
    tasklistProperties.getZeebeElasticsearch().setPrefix(tasklistZeebeRule.getPrefix());
    try {
      FieldSetter.setField(
          partitionHolder,
          PartitionHolder.class.getDeclaredField("zeebeClient"),
          clientRule.getClient());
    } catch (NoSuchFieldException e) {
      Assertions.fail("Failed to inject ZeebeClient into some of the beans");
    }
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
