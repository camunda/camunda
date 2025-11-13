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

import io.camunda.configuration.NodeIdProvider.S3;
import io.camunda.configuration.NodeIdProvider.Type;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({UnifiedConfiguration.class, UnifiedConfigurationHelper.class})
@ActiveProfiles("broker")
public class ClusterNodeIdProviderTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.node-id-provider.type=s3",
        "camunda.cluster.node-id-provider.s3.bucketName=bucketExample",
        "camunda.cluster.node-id-provider.s3.leaseDuration=PT10s",
      })
  class WithOnlyRequiredProperties {

    private final Camunda camunda;

    WithOnlyRequiredProperties(@Autowired final Camunda camunda) {
      this.camunda = camunda;
    }

    @Test
    void shouldSetWithOnlyRequiredProperties() {
      final var cluster = camunda.getCluster();
      assertThat(cluster.getNodeIdProvider()).returns(Type.S3, NodeIdProvider::getType);
      assertThat(cluster.getNodeIdProvider().s3())
          .returns("bucketExample", S3::getBucketName)
          .returns(Duration.ofSeconds(10), S3::getLeaseDuration);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.cluster.node-id-provider.type=s3",
        "camunda.cluster.node-id-provider.s3.bucketName=bucketExample",
        "camunda.cluster.node-id-provider.s3.leaseDuration=PT10s",
        "camunda.cluster.node-id-provider.s3.task-id=example-task-id",
        "camunda.cluster.node-id-provider.s3.endpoint=https://s3.example.com",
        "camunda.cluster.node-id-provider.s3.region=us-east-1",
        "camunda.cluster.node-id-provider.s3.accessKey=myAccessKey",
        "camunda.cluster.node-id-provider.s3.secretKey=mySecretKey",
      })
  class WithAllProperties {

    private final Camunda camunda;

    WithAllProperties(@Autowired final Camunda camunda) {
      this.camunda = camunda;
    }

    @Test
    void shouldSetWithAllProperties() {
      final var cluster = camunda.getCluster();
      assertThat(cluster.getNodeIdProvider()).returns(Type.S3, NodeIdProvider::getType);
      assertThat(cluster.getNodeIdProvider().s3())
          .returns("bucketExample", S3::getBucketName)
          .returns(Duration.ofSeconds(10), S3::getLeaseDuration)
          .returns(Optional.of("example-task-id"), S3::getTaskId)
          .returns(Optional.of("https://s3.example.com"), S3::getEndpoint)
          .returns(Optional.of("us-east-1"), S3::getRegion)
          .returns(Optional.of("myAccessKey"), S3::getAccessKey)
          .returns(Optional.of("mySecretKey"), S3::getSecretKey);
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
      assertThat(cluster.getNodeIdProvider()).returns(Type.STATIC, NodeIdProvider::getType);
    }

    @Test
    void shouldThrowExceptionWhenAccessingS3Config() {
      final var cluster = camunda.getCluster();
      assertThatThrownBy(() -> cluster.getNodeIdProvider().s3())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "Cannot access S3 configuration when dynamic node ID type is STATIC");
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.cluster.node-id-provider.type=static"})
  class WithTypeSetToNone {

    private final Camunda camunda;

    WithTypeSetToNone(@Autowired final Camunda camunda) {
      this.camunda = camunda;
    }

    @Test
    void shouldSetTypeToNone() {
      final var cluster = camunda.getCluster();
      assertThat(cluster.getNodeIdProvider()).returns(Type.STATIC, NodeIdProvider::getType);
    }

    @Test
    void shouldThrowExceptionWhenAccessingS3Config() {
      final var cluster = camunda.getCluster();
      assertThatThrownBy(() -> cluster.getNodeIdProvider().s3())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(
              "Cannot access S3 configuration when dynamic node ID type is STATIC");
    }
  }
}
