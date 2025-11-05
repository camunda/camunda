/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.DynamicNodeIdConfig.S3;
import io.camunda.configuration.DynamicNodeIdConfig.Type;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({UnifiedConfiguration.class, UnifiedConfigurationHelper.class})
@ActiveProfiles("broker")
public class ClusterDynamicNodeIdTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.dynamic-node-id.type=s3",
        "camunda.cluster.dynamic-node-id.s3.bucketName=bucketExample",
        "camunda.cluster.dynamic-node-id.s3.leaseDuration=PT10s",
      })
  class WithOnlyRequiredProperties {

    private final Camunda camunda;

    WithOnlyRequiredProperties(@Autowired final Camunda camunda) {
      this.camunda = camunda;
    }

    @Test
    void shouldSetWithOnlyRequiredProperties() {
      final var cluster = camunda.getCluster();
      assertThat(cluster.getDynamicNodeId()).returns(Type.S3, DynamicNodeIdConfig::getType);
      assertThat(cluster.getDynamicNodeId().s3())
          .returns("bucketExample", S3::getBucketName)
          .returns(Duration.ofSeconds(10), S3::getLeaseDuration);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.dynamic-node-id.type=s3",
        "camunda.cluster.dynamic-node-id.s3.bucketName=bucketExample",
        "camunda.cluster.dynamic-node-id.s3.leaseDuration=PT10s",
        "camunda.cluster.dynamic-node-id.s3.endpoint=https://s3.example.com",
        "camunda.cluster.dynamic-node-id.s3.region=us-east-1",
        "camunda.cluster.dynamic-node-id.s3.accessKey=myAccessKey",
        "camunda.cluster.dynamic-node-id.s3.secretKey=mySecretKey",
      })
  class WithAllProperties {

    private final Camunda camunda;

    WithAllProperties(@Autowired final Camunda camunda) {
      this.camunda = camunda;
    }

    @Test
    void shouldSetWithAllProperties() {
      final var cluster = camunda.getCluster();
      assertThat(cluster.getDynamicNodeId()).returns(Type.S3, DynamicNodeIdConfig::getType);
      assertThat(cluster.getDynamicNodeId().s3())
          .returns("bucketExample", S3::getBucketName)
          .returns(Duration.ofSeconds(10), S3::getLeaseDuration)
          .returns("https://s3.example.com", S3::getEndpoint)
          .returns("us-east-1", S3::getRegion)
          .returns("myAccessKey", S3::getAccessKey)
          .returns("mySecretKey", S3::getSecretKey);
    }
  }

  @Nested
  class WithTypeUnset {

    private final Camunda camunda;

    WithTypeUnset(@Autowired final Camunda camunda) {
      this.camunda = camunda;
    }

    @Test
    void shouldDefaultToNoneType() {
      final var cluster = camunda.getCluster();
      assertThat(cluster.getDynamicNodeId()).returns(Type.NONE, DynamicNodeIdConfig::getType);
    }

    @Test
    void shouldThrowExceptionWhenAccessingS3Config() {
      final var cluster = camunda.getCluster();
      assertThatThrownBy(() -> cluster.getDynamicNodeId().s3())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot access S3 configuration when dynamic node ID type is NONE");
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.cluster.dynamic-node-id.type=none"})
  class WithTypeSetToNone {

    private final Camunda camunda;

    WithTypeSetToNone(@Autowired final Camunda camunda) {
      this.camunda = camunda;
    }

    @Test
    void shouldSetTypeToNone() {
      final var cluster = camunda.getCluster();
      assertThat(cluster.getDynamicNodeId()).returns(Type.NONE, DynamicNodeIdConfig::getType);
    }

    @Test
    void shouldThrowExceptionWhenAccessingS3Config() {
      final var cluster = camunda.getCluster();
      assertThatThrownBy(() -> cluster.getDynamicNodeId().s3())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cannot access S3 configuration when dynamic node ID type is NONE");
    }
  }
}
