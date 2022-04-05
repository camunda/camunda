/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import java.util.List;
import org.assertj.core.api.Assertions;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.rest.HealthCheckTest.AddManagementPropertiesInitializer;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.OperateZeebeRule;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ZeebeImporter;
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
  private OperateProperties operateProperties;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  private OperateZeebeRule operateZeebeRule;

  @After
  public void cleanup() {
    if (operateZeebeRule != null) {
      operateZeebeRule.finished(null);
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
    operateZeebeRule.setZeebeEsClient(zeebeEsClient);

    operateZeebeRule.starting(null);
    operateProperties.getZeebeElasticsearch().setPrefix(operateZeebeRule.getPrefix());

    partitionHolder.setZeebeClient(operateZeebeRule.getClient());
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
    operateZeebeRule.starting(null);

    //then 2
    //data import is still working
    zeebeImporter.performOneRoundOfImport();

  }

}
