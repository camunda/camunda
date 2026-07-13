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
}
