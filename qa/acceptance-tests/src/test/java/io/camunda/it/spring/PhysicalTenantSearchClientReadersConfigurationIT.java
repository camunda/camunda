/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.spring;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.camunda.configuration.physicaltenants.PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.camunda.application.commons.search.NativeSearchClientsConfiguration;
import io.camunda.application.commons.search.PhysicalTenantSearchClientReadersConfiguration;
import io.camunda.application.commons.search.SearchClientConfiguration;
import io.camunda.application.commons.search.SearchClientReaderConfiguration;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.clients.CamundaSearchClients;
import io.camunda.search.clients.auth.AnonymousResourceAccessController;
import io.camunda.search.connect.tenant.SearchClients;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.auth.SecurityContext;
import io.camunda.security.core.authz.ResourceAccessController;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import jakarta.annotation.Resource;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig
@TestPropertySource(properties = {"camunda.data.secondary-storage.type=elasticsearch"})
@Import({
  NativeSearchClientsConfiguration.class,
  SearchClientReaderConfiguration.class,
  SearchClientConfiguration.class,
  PhysicalTenantSearchClientReadersConfiguration.class,
  PhysicalTenantSearchClientReadersConfigurationIT.TestConfig.class
})
public class PhysicalTenantSearchClientReadersConfigurationIT {

  static final String TENANT_A = "tenanta";
  static final String TENANT_B = "tenantb";

  static final long PROCESS_INSTANCE_KEY_A = 1001L;
  static final long PROCESS_INSTANCE_KEY_B = 2002L;

  @Autowired private CamundaSearchClients tenantAwareSearchClients;

  @Autowired private SearchClients searchClients;

  @Resource(name = "physicalTenantScopedIndexDescriptors")
  private Map<String, IndexDescriptors> tenantDescriptors;

  @Test
  void shouldWirePerTenantSearchClientsFromResolver() {
    assertThat(searchClients.esClients())
        .containsOnlyKeys(DEFAULT_PHYSICAL_TENANT_ID, TENANT_A, TENANT_B);
    assertThat(searchClients.osClients()).isEmpty();
    assertThat(tenantDescriptors).containsOnlyKeys(DEFAULT_PHYSICAL_TENANT_ID, TENANT_A, TENANT_B);
  }

  @Test
  void shouldRouteProcessInstanceReadsToCorrectTenantContainer() {
    final var clientsA = anonymous(tenantAwareSearchClients).withPhysicalTenant(TENANT_A);
    final var clientsB = anonymous(tenantAwareSearchClients).withPhysicalTenant(TENANT_B);

    final var processInstancesA = clientsA.searchProcessInstances(ProcessInstanceQuery.of(b -> b));
    final var processInstancesB = clientsB.searchProcessInstances(ProcessInstanceQuery.of(b -> b));

    assertThat(processInstancesA.items())
        .extracting(ProcessInstanceEntity::processInstanceKey)
        .containsExactly(PROCESS_INSTANCE_KEY_A);
    assertThat(processInstancesB.items())
        .extracting(ProcessInstanceEntity::processInstanceKey)
        .containsExactly(PROCESS_INSTANCE_KEY_B);
  }

  private static CamundaSearchClients anonymous(final CamundaSearchClients clients) {
    return clients.withSecurityContext(
        SecurityContext.of(b -> b.withAuthentication(CamundaAuthentication.anonymous())));
  }

  @Configuration(proxyBeanMethods = false)
  static class TestConfig {

    private static final String ELASTIC_PRODUCT_HEADER = "X-Elastic-Product";

    private static final String ELASTIC_PRODUCT_VALUE = "Elasticsearch";

    @Bean(initMethod = "start", destroyMethod = "stop")
    WireMockServer wmTenantA() {
      return new WireMockServer(options().dynamicPort());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    WireMockServer wmTenantB() {
      return new WireMockServer(options().dynamicPort());
    }

    @Bean
    MockEnvironment mockEnvironment(
        final WireMockServer wmTenantA, final WireMockServer wmTenantB) {
      stubFakeEs(wmTenantA, PROCESS_INSTANCE_KEY_A);
      stubFakeEs(wmTenantB, PROCESS_INSTANCE_KEY_B);
      final var env = new MockEnvironment();
      env.setProperty(
          "camunda.physical-tenants." + TENANT_A + ".data.secondary-storage.elasticsearch.url",
          "http://localhost:" + wmTenantA.port());
      env.setProperty(
          "camunda.physical-tenants."
              + TENANT_A
              + ".data.secondary-storage.elasticsearch.index-prefix",
          TENANT_A);
      env.setProperty(
          "camunda.physical-tenants." + TENANT_B + ".data.secondary-storage.elasticsearch.url",
          "http://localhost:" + wmTenantB.port());
      env.setProperty(
          "camunda.physical-tenants."
              + TENANT_B
              + ".data.secondary-storage.elasticsearch.index-prefix",
          TENANT_B);
      return env;
    }

    @Bean
    @Primary
    PhysicalTenantResolver physicalTenantResolverOverride(final MockEnvironment environment) {
      return PhysicalTenantResolver.of(environment, new Camunda());
    }

    /** {@link GatewayRestConfiguration} is a plain POJO; register it as a bean for injection. */
    @Bean
    GatewayRestConfiguration gatewayRestConfiguration() {
      return new GatewayRestConfiguration();
    }

    /**
     * Required only so that {@link CamundaSearchClients} can execute a read; access control is not
     * under test here.
     */
    @Bean
    ResourceAccessController testAnonymousResourceAccessController() {
      return new AnonymousResourceAccessController();
    }

    private static void stubFakeEs(final WireMockServer wm, final long processInstanceKey) {
      wm.stubFor(
          post(urlPathMatching("/.*/_search"))
              .willReturn(
                  aResponse()
                      .withStatus(200)
                      .withHeader("Content-Type", "application/json")
                      .withHeader(ELASTIC_PRODUCT_HEADER, ELASTIC_PRODUCT_VALUE)
                      .withBody(searchHitsJson(processInstanceKey))));
    }

    private static String searchHitsJson(final long processInstanceKey) {
      return """
          {"took":1,"timed_out":false,"_shards":{"total":1,"successful":1,"failed":0},\
          "hits":{"hits":[{"_index":"i","_id":"%d","_source":{"processInstanceKey":%d}}]}}
          """
          .formatted(processInstanceKey, processInstanceKey);
    }
  }
}
