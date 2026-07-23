/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Aws;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class PhysicalTenantAwsTest {

  private MockEnvironment environment;

  @BeforeEach
  void setUp() {
    environment = new MockEnvironment();
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  private PhysicalTenantResolver newResolver() {
    final Camunda camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(environment, camunda);
  }

  /**
   * Sets {@code properties} plus, per declared tenant, the {@code security.initialization} block
   * required of every explicit tenant and a distinct index prefix to pass storage isolation.
   */
  private void setProperties(final Map<String, Object> properties, final String... tenantIds) {
    final Map<String, Object> all = new HashMap<>(properties);
    for (final String tenantId : tenantIds) {
      all.put(
          "camunda.physical-tenants."
              + tenantId
              + ".security.initialization.default-roles.admin.users[0]",
          tenantId + "-admin");
      all.put(
          "camunda.physical-tenants."
              + tenantId
              + ".data.secondary-storage.elasticsearch.index-prefix",
          tenantId);
    }
    environment.getPropertySources().addFirst(new MapPropertySource("test", all));
  }

  @Test
  void shouldBindRootAwsSection() {
    // given
    setProperties(
        Map.of(
            "camunda.aws.access-key", "key",
            "camunda.aws.secret-key", "secret",
            "camunda.aws.session-token", "token"));

    // when
    final Aws aws = newResolver().forPhysicalTenant("default").getAws();

    // then
    assertThat(aws.getAccessKey()).isEqualTo("key");
    assertThat(aws.getSecretKey()).isEqualTo("secret");
    assertThat(aws.getSessionToken()).isEqualTo("token");
    assertThat(aws.hasStaticCredentials()).isTrue();
    assertThat(aws.hasWebIdentity()).isFalse();
  }

  @Test
  void shouldMergeTenantAwsOverrideWithRootFields() {
    // given a root web identity, and a tenant overriding only the role
    setProperties(
        Map.of(
            "camunda.aws.role-arn", "arn:aws:iam::111:role/default-tenant",
            "camunda.aws.web-identity-token-file", "/var/run/secrets/default-token",
            "camunda.physical-tenants.tenantb.aws.role-arn", "arn:aws:iam::222:role/tenant-b"),
        "tenantb");

    // when
    final Aws aws = newResolver().forPhysicalTenant("tenantb").getAws();

    // then the role is the tenant's while the token file is inherited from the root
    assertThat(aws.getRoleArn()).isEqualTo("arn:aws:iam::222:role/tenant-b");
    assertThat(aws.getWebIdentityTokenFile()).isEqualTo("/var/run/secrets/default-token");
  }

  @Test
  void shouldInheritRootAwsSectionWhenTenantOmitsIt() {
    // given
    setProperties(
        Map.of("camunda.aws.access-key", "key", "camunda.aws.secret-key", "secret"), "tenanta");

    // when
    final Aws aws = newResolver().forPhysicalTenant("tenanta").getAws();

    // then
    assertThat(aws.getAccessKey()).isEqualTo("key");
    assertThat(aws.getSecretKey()).isEqualTo("secret");
  }

  @Test
  void shouldTreatBlankValuesAsUnset() {
    // given an all-blank section, as produced by set-but-empty env vars
    setProperties(
        Map.of(
            "camunda.aws.access-key", "",
            "camunda.aws.secret-key", " "));

    // when
    final Aws aws = newResolver().forPhysicalTenant("default").getAws();

    // then the section is empty and falls back to the SDK default chains
    assertThat(aws.getAccessKey()).isNull();
    assertThat(aws.getSecretKey()).isNull();
    assertThat(aws.hasStaticCredentials()).isFalse();
  }
}
