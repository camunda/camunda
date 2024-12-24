/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.properties;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.spring.client.properties.CamundaClientProperties.ClientMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = CamundaClientPropertiesTestConfig.class,
    properties = {
      "camunda.client.cluster-id=my-cluster-id",
      "camunda.client.region=bru-2",
      "camunda.client.mode=saas",
      "camunda.client.auth.scope=zeebe-scope"
    })
public class ZeebeClientPropertiesSaasTest {
  @Autowired CamundaClientProperties properties;

  @Test
  void shouldPopulateBaseUrlsForSaas() {
    assertThat(properties.getGrpcAddress().toString())
        .isEqualTo("https://my-cluster-id.bru-2.zeebe.camunda.io");
    assertThat(properties.getRestAddress().toString())
        .isEqualTo("https://bru-2.zeebe.camunda.io/my-cluster-id");
    assertThat(properties.getPreferRestOverGrpc()).isEqualTo(false);
  }

  @Test
  void shouldLoadDefaultsSaas() {
    assertThat(properties.getMode()).isEqualTo(ClientMode.saas);
    assertThat(properties.getAuth().getIssuer())
        .isEqualTo("https://login.cloud.camunda.io/oauth/token");
    assertThat(properties.getEnabled()).isEqualTo(true);
    assertThat(properties.getAuth().getAudience()).isEqualTo("zeebe.camunda.io");
    assertThat(properties.getAuth().getScope()).isEqualTo("zeebe-scope");
  }
}
