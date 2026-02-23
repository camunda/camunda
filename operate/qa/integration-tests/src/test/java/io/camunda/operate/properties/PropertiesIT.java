/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.security.configuration.SecurityConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      // Unified Configuration classes
      UnifiedConfigurationHelper.class,
      OperatePropertiesOverride.class,
      UnifiedConfiguration.class,
      // ---
      TestApplicationWithNoBeans.class,
      DatabaseInfo.class,
      CamundaSecurityProperties.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = {WebappsConfigurationInitializer.class})
@ActiveProfiles({"test-properties", "operate", "standalone"})
public class PropertiesIT {

  @Autowired private OperateProperties operateProperties;
  @Autowired private SecurityConfiguration securityConfiguration;
  @Autowired private UnifiedConfiguration unifiedConfiguration;

  @Test
  // TODO extend for new properties
  public void testProperties() {
    // The file application-test-properties.yml only has data related to Elasticsearch.
    assumeTrue(
        unifiedConfiguration.getCamunda().getData().getSecondaryStorage().getType()
            == SecondaryStorageType.elasticsearch,
        "Skipping because DB is not Elasticsearch");

    assertThat(operateProperties.getBatchOperationMaxSize()).isEqualTo(500);
    assertThat(operateProperties.getElasticsearch().getClusterName()).isEqualTo("clusterName");
    assertThat(operateProperties.getElasticsearch().getHost()).isEqualTo("someHost");
    assertThat(operateProperties.getElasticsearch().getPort()).isEqualTo(12345);
    assertThat(operateProperties.getElasticsearch().getDateFormat())
        .isEqualTo("yyyy-MM-dd'T'HH:mm:ss.SSSZZ");
    assertThat(operateProperties.getElasticsearch().getBatchSize()).isEqualTo(111);
    assertThat(operateProperties.getZeebe().getGatewayAddress()).isEqualTo("someZeebeHost:999");
    assertThat(operateProperties.getOperationExecutor().getBatchSize()).isEqualTo(555);
    assertThat(operateProperties.getOperationExecutor().getWorkerId()).isEqualTo("someWorker");
    assertThat(operateProperties.getOperationExecutor().getLockTimeout()).isEqualTo(15000);
    assertThat(operateProperties.getOperationExecutor().isExecutorEnabled()).isFalse();
    assertThat(operateProperties.getIdentity().getIssuerUrl()).isEqualTo("https://issueUrl:555");
    assertThat(operateProperties.getIdentity().getIssuerBackendUrl())
        .isEqualTo("https://issuerBackendUrl:555");
    assertThat(operateProperties.getIdentity().getClientId()).isEqualTo("someClientId");
    assertThat(operateProperties.getIdentity().getClientSecret()).isEqualTo("jahktewpofsdifhsdg");
    assertThat(operateProperties.getIdentity().getAudience()).isEqualTo("operateAudience");

    // assert that it can be set from ${camunda.operate.identity.resourcePermissionsEnabled}
    // this relationship is defined in WebappsConfigurationInitializer
    assertThat(securityConfiguration.getAuthorizations().isEnabled()).isTrue();

    // assert that it can be set from ${camunda.operate.multiTenancy.enabled}
    // this relationship is defined in WebappsConfigurationInitializer
    assertThat(securityConfiguration.getMultiTenancy().isChecksEnabled()).isTrue();
  }
}
