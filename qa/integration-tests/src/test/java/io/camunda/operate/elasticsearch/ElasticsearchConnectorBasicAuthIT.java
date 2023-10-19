/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.elasticsearch;

import io.camunda.operate.connect.ElasticsearchConnector;
import io.camunda.operate.connect.OpensearchConnector;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.store.opensearch.client.sync.ZeebeRichOpenSearchClient;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.util.testcontainers.ContainerApplicationContextInitializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.util.Map;

import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = { TestApplicationWithNoBeans.class, OperateProperties.class,
      TestElasticSearchRepository.class, ElasticsearchConnector.class,
      TestOpenSearchRepository.class, RichOpenSearchClient.class, ZeebeRichOpenSearchClient.class, OpensearchConnector.class}
)
@ContextConfiguration(initializers = { ContainerApplicationContextInitializer.class})
public class ElasticsearchConnectorBasicAuthIT {
  @Autowired
  TestSearchRepository testSearchRepository;

  @Test
  public void canConnect(){
    assertTrue(testSearchRepository.isConnected());
    assertTrue(testSearchRepository.isZeebeConnected());
  }

}
