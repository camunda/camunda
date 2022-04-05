/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplicationWithNoBeans.class, TasklistProperties.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test-properties")
public class PropertiesTest {

  @Autowired private TasklistProperties tasklistProperties;

  @Test
  public void testProperties() {
    assertThat(tasklistProperties.getImporter().isStartLoadingDataOnStartup()).isFalse();
    assertThat(tasklistProperties.getBatchOperationMaxSize()).isEqualTo(500);
    assertThat(tasklistProperties.getElasticsearch().getClusterName()).isEqualTo("clusterName");
    assertThat(tasklistProperties.getElasticsearch().getHost()).isEqualTo("someHost");
    assertThat(tasklistProperties.getElasticsearch().getPort()).isEqualTo(12345);
    assertThat(tasklistProperties.getElasticsearch().getDateFormat()).isEqualTo("yyyy-MM-dd");
    assertThat(tasklistProperties.getElasticsearch().getBatchSize()).isEqualTo(111);
    assertThat(tasklistProperties.getZeebeElasticsearch().getClusterName())
        .isEqualTo("zeebeElasticClusterName");
    assertThat(tasklistProperties.getZeebeElasticsearch().getHost()).isEqualTo("someOtherHost");
    assertThat(tasklistProperties.getZeebeElasticsearch().getPort()).isEqualTo(54321);
    assertThat(tasklistProperties.getZeebeElasticsearch().getDateFormat()).isEqualTo("dd-MM-yyyy");
    assertThat(tasklistProperties.getZeebeElasticsearch().getBatchSize()).isEqualTo(222);
    assertThat(tasklistProperties.getZeebeElasticsearch().getPrefix()).isEqualTo("somePrefix");
    assertThat(tasklistProperties.getZeebe().getGatewayAddress()).isEqualTo("someZeebeHost:999");
  }
}
