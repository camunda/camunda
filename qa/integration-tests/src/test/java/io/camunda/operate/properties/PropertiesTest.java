/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.properties;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(
  classes = {TestApplicationWithNoBeans.class, OperateProperties.class},
  webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test-properties")
public class PropertiesTest {

  @Autowired
  private OperateProperties operateProperties;

  @Test
  //TODO extend for new properties
  public void testProperties() {
    assertThat(operateProperties.getImporter().isStartLoadingDataOnStartup()).isFalse();
    assertThat(operateProperties.getBatchOperationMaxSize()).isEqualTo(500);
    assertThat(operateProperties.getElasticsearch().getClusterName()).isEqualTo("clusterName");
    assertThat(operateProperties.getElasticsearch().getHost()).isEqualTo("someHost");
    assertThat(operateProperties.getElasticsearch().getPort()).isEqualTo(12345);
    assertThat(operateProperties.getElasticsearch().getDateFormat()).isEqualTo("yyyy-MM-dd");
    assertThat(operateProperties.getElasticsearch().getBatchSize()).isEqualTo(111);
    assertThat(operateProperties.getZeebeElasticsearch().getClusterName()).isEqualTo("zeebeElasticClusterName");
    assertThat(operateProperties.getZeebeElasticsearch().getHost()).isEqualTo("someOtherHost");
    assertThat(operateProperties.getZeebeElasticsearch().getPort()).isEqualTo(54321);
    assertThat(operateProperties.getZeebeElasticsearch().getDateFormat()).isEqualTo("dd-MM-yyyy");
    assertThat(operateProperties.getZeebeElasticsearch().getBatchSize()).isEqualTo(222);
    assertThat(operateProperties.getZeebeElasticsearch().getPrefix()).isEqualTo("somePrefix");
    assertThat(operateProperties.getZeebe().getGatewayAddress()).isEqualTo("someZeebeHost:999");
    assertThat(operateProperties.getOperationExecutor().getBatchSize()).isEqualTo(555);
    assertThat(operateProperties.getOperationExecutor().getWorkerId()).isEqualTo("someWorker");
    assertThat(operateProperties.getOperationExecutor().getLockTimeout()).isEqualTo(15000);
    assertThat(operateProperties.getOperationExecutor().isExecutorEnabled()).isFalse();
    assertThat(operateProperties.getIdentity().getIssuerUrl()).isEqualTo("https://issueUrl:555");
    assertThat(operateProperties.getIdentity().getIssuerBackendUrl()).isEqualTo("https://issuerBackendUrl:555");
    assertThat(operateProperties.getIdentity().getClientId()).isEqualTo("someClientId");
    assertThat(operateProperties.getIdentity().getClientSecret()).isEqualTo("jahktewpofsdifhsdg");
    assertThat(operateProperties.getIdentity().getAudience()).isEqualTo("operateAudience");
    assertThat(operateProperties.getIdentity().isResourcePermissionsEnabled()).isTrue();
  }

}
