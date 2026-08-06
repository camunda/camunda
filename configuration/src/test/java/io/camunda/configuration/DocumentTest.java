/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({UnifiedConfiguration.class, UnifiedConfigurationHelper.class})
class DocumentTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.document.default-store-id=aws1",
        "camunda.document.thread-pool-size=10",
        "camunda.document.aws.aws1.bucket-name=docs",
        "camunda.document.aws.aws1.bucket-path=prod/",
        "camunda.document.aws.aws1.region=eu-west-1",
        "camunda.document.aws.aws1.bucket-ttl=30",
        "camunda.document.aws.aws1.access-key=aws1-key",
        "camunda.document.aws.aws1.secret-key=aws1-secret",
        "camunda.document.gcp.gcp1.bucket-name=gcp-docs",
        "camunda.document.gcp.gcp1.prefix=temp/",
        "camunda.document.gcp.gcp1.credentials-path=/var/secrets/gcp1.json",
        "camunda.document.azure.az1.container-name=docs",
        "camunda.document.azure.az1.container-path=tenant-a/",
        "camunda.document.azure.az1.endpoint=https://account.blob.core.windows.net",
        "camunda.document.azure.az1.connection-string=AccountName=acc;AccountKey=az1-key",
        "camunda.document.local.local1.path=/var/camunda/documents"
      })
  class WithUnifiedDocumentConfiguration {
    private final UnifiedConfiguration unifiedConfiguration;

    WithUnifiedDocumentConfiguration(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldBindRootDocumentProperties() {
      assertThat(unifiedConfiguration.getCamunda().getDocument().getDefaultStoreId())
          .isEqualTo("aws1");
      assertThat(unifiedConfiguration.getCamunda().getDocument().getThreadPoolSize()).isEqualTo(10);
    }

    @Test
    void shouldBindAwsStoreProperties() {
      assertThat(unifiedConfiguration.getCamunda().getDocument().getAws().get("aws1"))
          .satisfies(
              awsStore -> {
                assertThat(awsStore.getBucketName()).isEqualTo("docs");
                assertThat(awsStore.getBucketPath()).isEqualTo("prod/");
                assertThat(awsStore.getRegion()).isEqualTo("eu-west-1");
                assertThat(awsStore.getBucketTtl()).isEqualTo(30L);
                assertThat(awsStore.getAccessKey()).isEqualTo("aws1-key");
                assertThat(awsStore.getSecretKey()).isEqualTo("aws1-secret");
              });
    }

    @Test
    void shouldBindGcpAzureAndLocalStoreProperties() {
      assertThat(unifiedConfiguration.getCamunda().getDocument().getGcp().get("gcp1"))
          .satisfies(
              gcpStore -> {
                assertThat(gcpStore.getBucketName()).isEqualTo("gcp-docs");
                assertThat(gcpStore.getPrefix()).isEqualTo("temp/");
                assertThat(gcpStore.getCredentialsPath()).isEqualTo("/var/secrets/gcp1.json");
              });

      assertThat(unifiedConfiguration.getCamunda().getDocument().getAzure().get("az1"))
          .satisfies(
              azureStore -> {
                assertThat(azureStore.getContainerName()).isEqualTo("docs");
                assertThat(azureStore.getContainerPath()).isEqualTo("tenant-a/");
                assertThat(azureStore.getEndpoint())
                    .isEqualTo("https://account.blob.core.windows.net");
                assertThat(azureStore.getConnectionString())
                    .isEqualTo("AccountName=acc;AccountKey=az1-key");
              });

      assertThat(unifiedConfiguration.getCamunda().getDocument().getLocal().get("local1"))
          .satisfies(
              localStore -> assertThat(localStore.getPath()).isEqualTo("/var/camunda/documents"));
    }
  }
}
