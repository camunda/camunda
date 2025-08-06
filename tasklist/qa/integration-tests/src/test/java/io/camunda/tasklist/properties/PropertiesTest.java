/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      TasklistPropertiesOverride.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test-properties")
public class PropertiesTest {

  @Autowired private TasklistProperties tasklistProperties;

  @Test
  public void testProperties() {
    assertThat(tasklistProperties.getImporter().isStartLoadingDataOnStartup()).isFalse();
    assertThat(tasklistProperties.getImporter().getCompletedReaderMinEmptyBatches()).isEqualTo(10);
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
