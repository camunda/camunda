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

import java.time.Duration;
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
        "camunda.secrets.stores.aws.aws-prod.region=eu-west-1",
        "camunda.secrets.stores.aws.aws-prod.path-prefix=camunda/",
        "camunda.secrets.stores.aws.aws-minimal.path-prefix=team/"
      })
  class WithAwsSecretsManagerStoreConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithAwsSecretsManagerStoreConfigured(
        @Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldBindAwsSecretsManagerStoreProperties() {
      // given the camunda.secrets.stores.aws.aws-prod.* properties are set
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the named store is present and its fields are bound
      assertThat(secrets.getStores().getAws().get("aws-prod"))
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
      assertThat(secrets.getStores().getAws().get("aws-prod"))
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
      assertThat(secrets.getStores().getAws().get("aws-prod").getContainerSecretId()).isNull();
    }

    @Test
    void shouldBindMultipleStoresKeyedById() {
      // given two aws stores are configured (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then both store ids are present
      assertThat(secrets.getStores().getAws()).containsKeys("aws-prod", "aws-minimal");
    }

    @Test
    void shouldAllowOptionalRegion() {
      // given aws-minimal has no region set (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then region is null and path-prefix is still bound
      assertThat(secrets.getStores().getAws().get("aws-minimal"))
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
        "camunda.secrets.stores.aws.batched.batch-enabled=true",
        "camunda.secrets.stores.aws.batched.batch-size=5"
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
      assertThat(secrets.getStores().getAws().get("batched"))
          .satisfies(
              store -> {
                assertThat(store.isBatchEnabled()).isTrue();
                assertThat(store.getBatchSize()).isEqualTo(5);
              });
    }
  }

  @Nested
  @TestPropertySource(
      properties = {"camunda.secrets.stores.aws.bundled.container-secret-id=app-config"})
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
      assertThat(secrets.getStores().getAws().get("bundled").getContainerSecretId())
          .isEqualTo("app-config");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.secrets.stores.aws.conflicted.batch-enabled=true",
        "camunda.secrets.stores.aws.conflicted.container-secret-id=app-config"
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
      assertThatThrownBy(stores::getAws).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.stores.aws.oversized.batch-size=50"})
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
      assertThatThrownBy(stores::getAws).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.stores.aws.blank.container-secret-id= "})
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
      assertThatThrownBy(stores::getAws).isInstanceOf(IllegalArgumentException.class);
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

      // then the aws map is empty
      assertThat(secrets.getStores().getAws()).isEmpty();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.secrets.stores.gcp.gcp-prod.project-id=my-gcp-project",
        "camunda.secrets.stores.gcp.gcp-prod.path-prefix=camunda-",
        "camunda.secrets.stores.gcp.gcp-prod.endpoint=secretmanager.example.com:443",
        "camunda.secrets.stores.gcp.gcp-minimal.project-id=minimal-project"
      })
  class WithGcpStoreConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithGcpStoreConfigured(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldBindGcpStoreProperties() {
      // given the camunda.secrets.stores.gcp.gcp-prod.* properties are set
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the named store is present and its fields are bound
      assertThat(secrets.getStores().getGcp().get("gcp-prod"))
          .satisfies(
              store -> {
                assertThat(store.getProjectId()).isEqualTo("my-gcp-project");
                assertThat(store.getPathPrefix()).isEqualTo("camunda-");
                assertThat(store.getEndpoint()).isEqualTo("secretmanager.example.com:443");
              });
    }

    @Test
    void shouldDefaultContainerSecretIdToNull() {
      // given container-secret-id is not set for gcp-prod (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then it defaults to the flat one-secret-per-reference mode
      assertThat(secrets.getStores().getGcp().get("gcp-prod").getContainerSecretId()).isNull();
    }

    @Test
    void shouldBindMultipleStoresKeyedById() {
      // given two gcp stores are configured (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then both store ids are present
      assertThat(secrets.getStores().getGcp()).containsKeys("gcp-prod", "gcp-minimal");
    }

    @Test
    void shouldAllowOptionalPathPrefixAndEndpoint() {
      // given gcp-minimal has only project-id set (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then project-id is bound while the optional fields stay null
      assertThat(secrets.getStores().getGcp().get("gcp-minimal"))
          .satisfies(
              store -> {
                assertThat(store.getProjectId()).isEqualTo("minimal-project");
                assertThat(store.getPathPrefix()).isNull();
                assertThat(store.getEndpoint()).isNull();
              });
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.secrets.stores.gcp.bundled.project-id=my-gcp-project",
        "camunda.secrets.stores.gcp.bundled.container-secret-id=app-config"
      })
  class WithGcpContainerSecretIdConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithGcpContainerSecretIdConfigured(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldBindContainerSecretId() {
      // given container-secret-id is set (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then it is bound onto the named store
      assertThat(secrets.getStores().getGcp().get("bundled").getContainerSecretId())
          .isEqualTo("app-config");
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.stores.gcp.blank.project-id= "})
  class WithBlankGcpProjectId {
    private final UnifiedConfiguration unifiedConfiguration;

    WithBlankGcpProjectId(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectBlankProjectId() {
      // given project-id is set to a blank string (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();
      final Secrets.Stores stores = secrets.getStores();

      // then reading the store map throws
      assertThatThrownBy(stores::getGcp)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("project-id must not be blank");
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.stores.gcp.blank.endpoint= "})
  class WithBlankGcpEndpoint {
    private final UnifiedConfiguration unifiedConfiguration;

    WithBlankGcpEndpoint(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectBlankEndpoint() {
      // given endpoint is set to a blank string (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();
      final Secrets.Stores stores = secrets.getStores();

      // then reading the store map throws
      assertThatThrownBy(stores::getGcp)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("endpoint must not be blank");
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.stores.gcp.blank.container-secret-id= "})
  class WithBlankGcpContainerSecretId {
    private final UnifiedConfiguration unifiedConfiguration;

    WithBlankGcpContainerSecretId(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectBlankContainerSecretId() {
      // given container-secret-id is set to a blank string (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();
      final Secrets.Stores stores = secrets.getStores();

      // then reading the store map throws
      assertThatThrownBy(stores::getGcp)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("container-secret-id must not be blank");
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.stores.gcp.no-project.path-prefix=camunda-"})
  class WithGcpStoreWithoutProjectId {
    private final UnifiedConfiguration unifiedConfiguration;

    WithGcpStoreWithoutProjectId(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldAllowOmittedProjectId() {
      // given a gcp store with no project-id set (see @TestPropertySource): project-id is optional
      // and resolved from the environment/metadata via Application Default Credentials
      // when the unified configuration is bound and the validating getter is read
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the store binds without throwing and project-id stays null
      assertThat(secrets.getStores().getGcp().get("no-project").getProjectId()).isNull();
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.stores.gcp.badprefix.path-prefix=camunda/"})
  class WithGcpPathPrefixContainingInvalidChars {
    private final UnifiedConfiguration unifiedConfiguration;

    WithGcpPathPrefixContainingInvalidChars(
        @Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectPathPrefixWithCharsOutsideSecretIdCharset() {
      // given path-prefix contains a slash, invalid in a GCP secret id (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();
      final Secrets.Stores stores = secrets.getStores();

      // then reading the store map throws
      assertThatThrownBy(stores::getGcp)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("path-prefix must contain only [a-zA-Z0-9_-]");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {"camunda.secrets.stores.gcp.badcontainer.container-secret-id=app/config"})
  class WithGcpContainerSecretIdContainingInvalidChars {
    private final UnifiedConfiguration unifiedConfiguration;

    WithGcpContainerSecretIdContainingInvalidChars(
        @Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectContainerSecretIdWithCharsOutsideSecretIdCharset() {
      // given container-secret-id contains a slash, invalid in a GCP secret id
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();
      final Secrets.Stores stores = secrets.getStores();

      // then reading the store map throws
      assertThatThrownBy(stores::getGcp)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("container-secret-id must contain only [a-zA-Z0-9_-]");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // 256 'a's (4 x 64), one over the 255 GCP secret-id cap
        "camunda.secrets.stores.gcp.toolong.container-secret-id="
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      })
  class WithGcpEffectiveContainerSecretIdTooLong {
    private final UnifiedConfiguration unifiedConfiguration;

    WithGcpEffectiveContainerSecretIdTooLong(
        @Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectEffectiveContainerSecretIdOver255Chars() {
      // given a 256-char container-secret-id, over the 255 GCP secret-id cap
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();
      final Secrets.Stores stores = secrets.getStores();

      // then reading the store map throws
      assertThatThrownBy(stores::getGcp)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must be at most 255 characters");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // path-prefix (64) + container-secret-id (192) = 256, one over the 255 cap, while each
        // part alone is within it: exercises the combined "effective" length check
        "camunda.secrets.stores.gcp.combined.path-prefix="
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "camunda.secrets.stores.gcp.combined.container-secret-id="
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      })
  class WithGcpEffectiveContainerSecretIdTooLongViaPrefix {
    private final UnifiedConfiguration unifiedConfiguration;

    WithGcpEffectiveContainerSecretIdTooLongViaPrefix(
        @Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectWhenPathPrefixPlusContainerSecretIdExceeds255Chars() {
      // given path-prefix + container-secret-id sum to 256, though neither alone exceeds 255
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();
      final Secrets.Stores stores = secrets.getStores();

      // then reading the store map throws on the combined length
      assertThatThrownBy(stores::getGcp)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must be at most 255 characters");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // path-prefix (63) + container-secret-id (192) = 255, exactly at the cap: must bind cleanly
        "camunda.secrets.stores.gcp.atlimit.path-prefix="
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "camunda.secrets.stores.gcp.atlimit.container-secret-id="
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      })
  class WithGcpEffectiveContainerSecretIdAtLimit {
    private final UnifiedConfiguration unifiedConfiguration;

    WithGcpEffectiveContainerSecretIdAtLimit(
        @Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldAllowEffectiveContainerSecretIdOfExactly255Chars() {
      // given path-prefix + container-secret-id sum to exactly 255, the GCP secret-id cap
      // when the unified configuration is bound and the validating getter is read
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the store binds without throwing
      assertThat(secrets.getStores().getGcp().get("atlimit").getContainerSecretId()).hasSize(192);
    }
  }

  @Nested
  class WithoutGcpStoreConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithoutGcpStoreConfigured(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldDefaultToEmptyGcpMap() {
      // given no camunda.secrets.* property is set
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the gcp map is empty
      assertThat(secrets.getStores().getGcp()).isEmpty();
    }
  }

  @Nested
  class WithoutCacheConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithoutCacheConfigured(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldDefaultCacheTtlToTwentyMinutes() {
      // given no camunda.secrets.cache.* property is set
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the ttl is the documented default
      assertThat(secrets.getCache().getTtl()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void shouldDefaultCacheMaxSizeToOneThousand() {
      // given no camunda.secrets.cache.* property is set
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the max size is the documented default
      assertThat(secrets.getCache().getMaxSize()).isEqualTo(1000);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {"camunda.secrets.cache.ttl=5m", "camunda.secrets.cache.max-size=50"})
  class WithCacheConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithCacheConfigured(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldBindCacheTtlAndMaxSize() {
      // given camunda.secrets.cache.ttl and .max-size are set (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then both values are bound
      assertThat(secrets.getCache().getTtl()).isEqualTo(Duration.ofMinutes(5));
      assertThat(secrets.getCache().getMaxSize()).isEqualTo(50);
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.cache.ttl=2h"})
  class WithCacheTtlCoarserThanMinutes {
    private final UnifiedConfiguration unifiedConfiguration;

    WithCacheTtlCoarserThanMinutes(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldAcceptCacheTtlCoarserThanMinutes() {
      // given a ttl of two hours, which is a whole number of minutes
      // when the unified configuration is bound and the validating getter is read
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then it binds without throwing: minute granularity means a whole multiple of a minute, not
      // a value that has to be expressed in minutes
      assertThat(secrets.getCache().getTtl()).isEqualTo(Duration.ofHours(2));
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.cache.ttl=30s"})
  class WithSubMinuteCacheTtl {
    private final UnifiedConfiguration unifiedConfiguration;

    WithSubMinuteCacheTtl(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectCacheTtlBelowOneMinute() {
      // given a ttl below the one-minute minimum
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // when the validating getter is read
      // then it is rejected with the property path
      assertThatThrownBy(secrets::getCache)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("camunda.secrets.cache.ttl")
          .hasMessageContaining("at least 1 minute");
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.cache.ttl=90s"})
  class WithNonMinuteGranularCacheTtl {
    private final UnifiedConfiguration unifiedConfiguration;

    WithNonMinuteGranularCacheTtl(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectCacheTtlThatIsNotAWholeNumberOfMinutes() {
      // given a ttl above the minimum but not a whole number of minutes
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // when the validating getter is read
      // then the granularity rule rejects it, not the minimum
      assertThatThrownBy(secrets::getCache)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("camunda.secrets.cache.ttl")
          .hasMessageContaining("whole number of minutes");
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.cache.max-size=0"})
  class WithZeroCacheMaxSize {
    private final UnifiedConfiguration unifiedConfiguration;

    WithZeroCacheMaxSize(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectCacheMaxSizeBelowOne() {
      // given a max size below 1, which would cache nothing at all
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // when the validating getter is read
      // then it is rejected with the property path
      assertThatThrownBy(secrets::getCache)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("camunda.secrets.cache.max-size")
          .hasMessageContaining("at least 1");
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.cache.ttl="})
  class WithEmptyCacheTtl {
    private final UnifiedConfiguration unifiedConfiguration;

    WithEmptyCacheTtl(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldFallBackToTheDefaultTtlWhenTheValueIsEmpty() {
      // given an explicitly empty ttl, which an env-var-driven deployment produces routinely
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the default is kept, so no null ever reaches the cache and the minimum is not tripped
      assertThat(secrets.getCache().getTtl()).isEqualTo(Duration.ofMinutes(20));
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.cache.max-size="})
  class WithEmptyCacheMaxSize {
    private final UnifiedConfiguration unifiedConfiguration;

    WithEmptyCacheMaxSize(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldFallBackToTheDefaultMaxSizeWhenTheValueIsEmpty() {
      // given an explicitly empty max size, which an env-var-driven deployment produces routinely
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the default is kept rather than binding as 0, which the minimum would reject at
      // startup
      assertThat(secrets.getCache().getMaxSize()).isEqualTo(1000);
    }
  }

  @Nested
  class CacheDefaults {

    @Test
    void shouldKeepTheDefaultTtlWhenSetToNull() {
      // given the default cache configuration
      final Secrets.Cache cache = new Secrets.Cache();

      // when a caller sets the ttl to null directly, which Spring's binder never does
      cache.setTtl(null);

      // then the default stands, so getTtl() keeps its NullMarked contract for the cache factory
      assertThat(cache.getTtl()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void shouldKeepTheDefaultMaxSizeWhenSetToNull() {
      // given the default cache configuration
      final Secrets.Cache cache = new Secrets.Cache();

      // when a caller sets the max size to null directly
      cache.setMaxSize(null);

      // then the default stands, so getMaxSize() can keep handing an int to the cache factory
      assertThat(cache.getMaxSize()).isEqualTo(1000);
    }
  }

  @Nested
  class WithoutMaxConcurrencyConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithoutMaxConcurrencyConfigured(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldDefaultMaxConcurrencyToEight() {
      // given no camunda.secrets.max-concurrency property is set
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the max concurrency is the documented default
      assertThat(secrets.getMaxConcurrency()).isEqualTo(8);
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.max-concurrency=3"})
  class WithMaxConcurrencyConfigured {
    private final UnifiedConfiguration unifiedConfiguration;

    WithMaxConcurrencyConfigured(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldBindMaxConcurrency() {
      // given camunda.secrets.max-concurrency is set (see @TestPropertySource)
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then it is bound
      assertThat(secrets.getMaxConcurrency()).isEqualTo(3);
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.max-concurrency=0"})
  class WithZeroMaxConcurrency {
    private final UnifiedConfiguration unifiedConfiguration;

    WithZeroMaxConcurrency(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldRejectMaxConcurrencyBelowOne() {
      // given a max concurrency below 1, which would resolve nothing at all
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // when the validating getter is read
      // then it is rejected with the property path
      assertThatThrownBy(secrets::getMaxConcurrency)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("camunda.secrets.max-concurrency")
          .hasMessageContaining("at least 1");
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.secrets.max-concurrency="})
  class WithEmptyMaxConcurrency {
    private final UnifiedConfiguration unifiedConfiguration;

    WithEmptyMaxConcurrency(@Autowired final UnifiedConfiguration unifiedConfiguration) {
      this.unifiedConfiguration = unifiedConfiguration;
    }

    @Test
    void shouldFallBackToTheDefaultMaxConcurrencyWhenTheValueIsEmpty() {
      // given an explicitly empty max concurrency, which an env-var-driven deployment produces
      // routinely
      // when the unified configuration is bound
      final Secrets secrets = unifiedConfiguration.getCamunda().getSecrets();

      // then the default is kept rather than binding as 0, which the minimum would reject at
      // startup
      assertThat(secrets.getMaxConcurrency()).isEqualTo(8);
    }
  }

  @Nested
  class MaxConcurrencyDefaults {

    @Test
    void shouldKeepTheDefaultMaxConcurrencyWhenSetToNull() {
      // given the default secrets configuration
      final Secrets secrets = new Secrets();

      // when a caller sets max concurrency to null directly, which Spring's binder never does
      secrets.setMaxConcurrency(null);

      // then the default stands, so getMaxConcurrency() can keep handing an int to the pool
      assertThat(secrets.getMaxConcurrency()).isEqualTo(8);
    }
  }
}
