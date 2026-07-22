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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({UnifiedConfiguration.class, UnifiedConfigurationHelper.class})
class SecretsTest {

  @Nested
  @TestPropertySource(
      properties = {"camunda.secrets.stores.file.mystore.path=/etc/camunda/secrets.txt"})
  class WithFileStoreConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithFileStoreConfigured(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldBindFileStorePath() {
      // given the property camunda.secrets.stores.file.mystore.path is set (see
      // @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the named file store is present and its path is bound
      assertThat(secrets.getStores().getFile()).containsKey("mystore");
      assertThat(secrets.getStores().getFile().get("mystore").getPath())
          .isEqualTo("/etc/camunda/secrets.txt");
    }
  }

  @Nested
  class WithoutFileStoreConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithoutFileStoreConfigured(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldDefaultToEmptyFileMap() {
      // given no camunda.secrets.* property is set
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then stores is non-null (field initializer) and the file map is empty
      assertThat(secrets.getStores()).isNotNull();
      assertThat(secrets.getStores().getFile()).isEmpty();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.secrets.stores.aws-secrets-manager.aws-prod.region=eu-west-1",
        "camunda.secrets.stores.aws-secrets-manager.aws-prod.path-prefix=camunda/",
        "camunda.secrets.stores.aws-secrets-manager.aws-minimal.path-prefix=team/"
      })
  class WithAwsSecretsManagerStoreConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithAwsSecretsManagerStoreConfigured(
        @Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldBindAwsSecretsManagerStoreProperties() {
      // given the camunda.secrets.stores.aws-secrets-manager.aws-prod.* properties are set
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the named store is present and its fields are bound
      assertThat(secrets.getStores().getAwsSecretsManager().get("aws-prod"))
          .satisfies(
              store -> {
                assertThat(store.getRegion()).isEqualTo("eu-west-1");
                assertThat(store.getPathPrefix()).isEqualTo("camunda/");
              });
    }

    @Test
    void shouldDefaultBatchingToDisabled() {
      // given batch-enabled/batch-size are not set for aws-prod (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then batching defaults to off, with the standard AWS batch size as an inert default
      assertThat(secrets.getStores().getAwsSecretsManager().get("aws-prod"))
          .satisfies(
              store -> {
                assertThat(store.isBatchEnabled()).isFalse();
                assertThat(store.getBatchSize()).isEqualTo(20);
              });
    }

    @Test
    void shouldDefaultContainerSecretIdToNull() {
      // given container-secret-id is not set for aws-prod (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then it defaults to the flat one-secret-per-reference mode
      assertThat(secrets.getStores().getAwsSecretsManager().get("aws-prod").getContainerSecretId())
          .isNull();
    }

    @Test
    void shouldBindMultipleStoresKeyedById() {
      // given two aws-secrets-manager stores are configured (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then both store ids are present
      assertThat(secrets.getStores().getAwsSecretsManager())
          .containsKeys("aws-prod", "aws-minimal");
    }

    @Test
    void shouldAllowOptionalRegion() {
      // given aws-minimal has no region set (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then region is null and path-prefix is still bound
      assertThat(secrets.getStores().getAwsSecretsManager().get("aws-minimal"))
          .satisfies(
              store -> {
                assertThat(store.getRegion()).isNull();
                assertThat(store.getPathPrefix()).isEqualTo("team/");
              });
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.secrets.stores.aws-secrets-manager.batched.batch-enabled=true",
        "camunda.secrets.stores.aws-secrets-manager.batched.batch-size=5"
      })
  class WithBatchingConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithBatchingConfigured(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldBindBatchEnabledAndBatchSize() {
      // given batch-enabled/batch-size are set (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then both are bound onto the named store
      assertThat(secrets.getStores().getAwsSecretsManager().get("batched"))
          .satisfies(
              store -> {
                assertThat(store.isBatchEnabled()).isTrue();
                assertThat(store.getBatchSize()).isEqualTo(5);
              });
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.secrets.stores.aws-secrets-manager.bundled.container-secret-id=app-config"
      })
  class WithContainerSecretIdConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithContainerSecretIdConfigured(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldBindContainerSecretId() {
      // given container-secret-id is set (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then it is bound onto the named store
      assertThat(secrets.getStores().getAwsSecretsManager().get("bundled").getContainerSecretId())
          .isEqualTo("app-config");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.secrets.stores.aws-secrets-manager.conflicted.batch-enabled=true",
        "camunda.secrets.stores.aws-secrets-manager.conflicted.container-secret-id=app-config"
      })
  class WithBatchingAndContainerSecretIdBothConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithBatchingAndContainerSecretIdBothConfigured(
        @Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectBatchEnabledWithContainerSecretId() {
      // given batch-enabled and container-secret-id are both set (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();
      final Secrets.Stores stores = secrets.getStores();

      // then reading the store map throws, since the combination is contradictory
      assertThatThrownBy(stores::getAwsSecretsManager).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {"camunda.secrets.stores.aws-secrets-manager.oversized.batch-size=50"})
  class WithBatchSizeOutOfRange {
    private final UnifiedConfiguration unifiedConfiguration;

    WithBatchSizeOutOfRange(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectBatchSizeAboveAwsCap() {
      // given batch-size is set above AWS's cap of 20 (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();
      final Secrets.Stores stores = secrets.getStores();

      // then reading the store map throws
      assertThatThrownBy(stores::getAwsSecretsManager).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {"camunda.secrets.stores.aws-secrets-manager.blank.container-secret-id= "})
  class WithBlankContainerSecretId {
    private final UnifiedConfiguration unifiedConfiguration;

    WithBlankContainerSecretId(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectBlankContainerSecretId() {
      // given container-secret-id is set to a blank string (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();
      final Secrets.Stores stores = secrets.getStores();

      // then reading the store map throws
      assertThatThrownBy(stores::getAwsSecretsManager).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class WithoutAwsSecretsManagerStoreConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithoutAwsSecretsManagerStoreConfigured(
        @Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldDefaultToEmptyAwsSecretsManagerMap() {
      // given no camunda.secrets.* property is set
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the aws-secrets-manager map is empty
      assertThat(secrets.getStores().getAwsSecretsManager()).isEmpty();
    }
  }
}
