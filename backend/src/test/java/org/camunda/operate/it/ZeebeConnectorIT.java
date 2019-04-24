/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import org.assertj.core.api.Assertions;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.rest.HealthCheckRestService;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.OperateZeebeRule;
import org.camunda.operate.util.ZeebeClientRule;
import org.camunda.operate.zeebeimport.PartitionHolder;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import io.zeebe.test.EmbeddedBrokerRule;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ZeebeConnectorIT extends OperateIntegrationTest {

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private ZeebeImporter zeebeImporter;

  @Autowired
  private PartitionHolder partitionHolder;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  private OperateZeebeRule operateZeebeRule;

  private ZeebeClientRule clientRule;

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private MockMvc mockMvc;

  @Before
  public void starting() {
    this.mockMvc = mockMvcTestRule.getMockMvc();
  }

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
    MockHttpServletRequestBuilder request = get(HealthCheckRestService.HEALTH_CHECK_URL);
    mockMvc.perform(request)
      .andExpect(status().isOk())
      .andReturn();
    //import is working fine
    zeebeImporter.performOneRoundOfImport();
    //partition list is empty
    Assertions.assertThat(partitionHolder.getPartitionIds()).isEmpty();

    //when 2
    //Zeebe is started
    startZeebe();

    //then 2
    //data import is working
    zeebeImporter.performOneRoundOfImport();
    //partition list is not empty
    Assertions.assertThat(partitionHolder.getPartitionIds()).isNotEmpty();

  }

  private void startZeebe() {
    operateZeebeRule = new OperateZeebeRule(EmbeddedBrokerRule.DEFAULT_CONFIG_FILE);
    try {
      FieldSetter.setField(operateZeebeRule, OperateZeebeRule.class.getDeclaredField("operateProperties"), operateProperties);
      FieldSetter.setField(operateZeebeRule, OperateZeebeRule.class.getDeclaredField("zeebeEsClient"), zeebeEsClient);
    } catch (NoSuchFieldException e) {
      Assertions.fail("Failed to inject ZeebeClient into some of the beans");
    }
    clientRule = new ZeebeClientRule(operateZeebeRule.getBrokerRule());
    operateZeebeRule.starting(null);
    clientRule.before();
    operateProperties.getZeebeElasticsearch().setPrefix(operateZeebeRule.getPrefix());
    try {
      FieldSetter.setField(partitionHolder, PartitionHolder.class.getDeclaredField("zeebeClient"), clientRule.getClient());
    } catch (NoSuchFieldException e) {
      Assertions.fail("Failed to inject ZeebeClient into some of the beans");
    }
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
