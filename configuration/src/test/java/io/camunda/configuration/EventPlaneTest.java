/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import java.net.URI;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
class EventPlaneTest {

  @Nested
  class WithNoneSet {
    final BrokerBasedProperties brokerCfg;

    WithNoneSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotRegister() {
      assertThat(brokerCfg.getEventPlane().getEndpoint()).isNull();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.event-plane.endpoint=http://event-plane:8080",
        "camunda.event-plane.username=demo",
        "camunda.event-plane.password=demo-password",
        "camunda.event-plane.client-id=my-client",
        "camunda.event-plane.client-secret=my-secret",
        "camunda.event-plane.token-url=http://idp/token",
        "camunda.event-plane.audience=event-plane",
      })
  class WithAllSet {
    final BrokerBasedProperties brokerCfg;

    WithAllSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldPassEverythingToTheBroker() {
      final var eventPlane = brokerCfg.getEventPlane();
      assertThat(eventPlane.getEndpoint()).isEqualTo(URI.create("http://event-plane:8080"));
      assertThat(eventPlane.getUsername()).isEqualTo("demo");
      assertThat(eventPlane.getPassword()).isEqualTo("demo-password");
      assertThat(eventPlane.getClientId()).isEqualTo("my-client");
      assertThat(eventPlane.getClientSecret()).isEqualTo("my-secret");
      assertThat(eventPlane.getTokenUrl()).isEqualTo(URI.create("http://idp/token"));
      assertThat(eventPlane.getAudience()).isEqualTo("event-plane");
    }

    @Test
    void shouldKeepSecretsOutOfToString() {
      assertThat(brokerCfg.getEventPlane().toString()).doesNotContain("demo-password", "my-secret");
    }
  }

  @Test
  void shouldLetEachPhysicalTenantNameItsOwnEventPlane() {
    // given
    final var environment = new MockEnvironment();
    environment.setProperty("camunda.event-plane.endpoint", "http://shared:8080");
    environment.setProperty(
        "camunda.physical-tenants.tenanta.event-plane.endpoint", "http://own:8080");
    // What every tenant needs of its own, which isn't what this is about.
    environment.setProperty(
        "camunda.physical-tenants.tenanta.security.authorizations.enabled", "false");
    environment.setProperty(
        "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
        "tenanta");
    UnifiedConfigurationHelper.setCustomEnvironment(environment);
    try {
      // when
      final var root =
          Binder.get(environment).bindOrCreate(Camunda.PREFIX, Bindable.of(Camunda.class));
      final var byTenant =
          PhysicalTenantResolver.of(environment, root)
              .mapValues(BrokerBasedPropertiesOverride::convert);

      // then
      assertThat(byTenant.get(DEFAULT_PHYSICAL_TENANT_ID).getEventPlane().getEndpoint())
          .isEqualTo(URI.create("http://shared:8080"));
      assertThat(byTenant.get("tenanta").getEventPlane().getEndpoint())
          .isEqualTo(URI.create("http://own:8080"));
    } finally {
      UnifiedConfigurationHelper.setCustomEnvironment(null);
    }
  }
}
