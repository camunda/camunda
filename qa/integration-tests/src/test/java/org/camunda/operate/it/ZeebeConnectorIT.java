/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.HealthCheckTest.AddManagementPropertiesInitializer;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.EmbeddedZeebeConfigurer;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.OperateZeebeRule;
import org.camunda.operate.util.TestApplication;
import org.camunda.operate.util.ZeebeClientRule;
import org.camunda.operate.zeebe.PartitionHolder;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
    classes = { TestApplication.class},
    properties = {OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
        OperateProperties.PREFIX + ".zeebe.gatewayAddress = localhost:55500"})
@ContextConfiguration(initializers = AddManagementPropertiesInitializer.class)
public class ZeebeConnectorIT extends OperateIntegrationTest {

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private ZeebeImporter zeebeImporter;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private EmbeddedZeebeConfigurer embeddedZeebeConfigurer;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  private OperateZeebeRule operateZeebeRule;

  private ZeebeClientRule clientRule;

  @After
  public void cleanup() {
    if (operateZeebeRule != null) {
      operateZeebeRule.finished(null);
    }
    if (clientRule != null) {
      clientRule.after();
    }
  }

  @Test
  public void testZeebeConnection() throws Exception {
    //when 1
    //no Zeebe broker is running

    //then 1
    //application context must be successfully started
    getRequest("/actuator/health/liveness");
    //import is working fine
    zeebeImporter.performOneRoundOfImport();
    //partition list is empty
    Assertions.assertThat(getPartitionIds()).isEmpty();

    //when 2
    //Zeebe is started
    startZeebe();

    //then 2
    //data import is working
    zeebeImporter.performOneRoundOfImport();
    //partition list is not empty
    Assertions.assertThat(getPartitionIds()).isNotEmpty();

  }

  private List<Integer> getPartitionIds() {
    return (List<Integer>) ReflectionTestUtils
        .getField(partitionHolder, "partitionIds");
  }

  private void startZeebe() {
    operateZeebeRule = new OperateZeebeRule();
    operateZeebeRule.setOperateProperties(operateProperties);
    operateZeebeRule.setEmbeddedZeebeConfigurer(embeddedZeebeConfigurer);
    operateZeebeRule.setZeebeEsClient(zeebeEsClient);

    clientRule = new ZeebeClientRule(operateZeebeRule.getBrokerRule());
    operateZeebeRule.starting(null);
    clientRule.before();
    operateProperties.getZeebeElasticsearch().setPrefix(operateZeebeRule.getPrefix());

    partitionHolder.setZeebeClient(clientRule.getClient());
  }

  @Test
  public void testRecoverAfterZeebeRestart() throws Exception {
    //when 1
    //Zeebe is started
    startZeebe();

    //then 1
    //data import is working
    zeebeImporter.performOneRoundOfImport();

    //when 2
    //Zeebe is restarted
    operateZeebeRule.finished(null);
    clientRule.after();
    operateZeebeRule.starting(null);
    clientRule.before();

    //then 2
    //data import is still working
    zeebeImporter.performOneRoundOfImport();

  }

}
