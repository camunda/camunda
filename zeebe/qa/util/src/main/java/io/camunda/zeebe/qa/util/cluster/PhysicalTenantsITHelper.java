/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.camunda.client.CamundaClientBuilder;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Reusable helper for booting a single {@link TestStandaloneBroker} that serves several physical
 * tenants, and for addressing each of them over gRPC and REST. Every physical tenant, including the
 * {@code default} one, must be declared explicitly with its {@link Storage}. The helper only emits
 * configuration and builds clients / REST base URLs; the broker lifecycle stays with the existing
 * {@code @ZeebeIntegration} / {@code @TestZeebe} extension:
 *
 * <pre>{@code
 * @ZeebeIntegration
 * final class MyIsolationIT {
 *   private static final PhysicalTenantsITHelper TENANTS =
 *       PhysicalTenantsITHelper.builder()
 *           .withTenant("default", Storage.rdbmsH2("default"))
 *           .withTenant("tenanta", Storage.rdbmsH2("tenanta"))
 *           .build();
 *
 *   @TestZeebe
 *   private final TestStandaloneBroker broker =
 *       TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());
 *
 *   @Test
 *   void shouldIsolate() {
 *     try (final var client = TENANTS.newClientBuilder(broker, "tenanta").build()) {
 *       // gRPC scoped to tenanta
 *     }
 *     final URI restBase = TENANTS.restBaseFor(broker, "tenanta"); // .../physical-tenants/tenanta/v2
 *   }
 * }
 * }</pre>
 */
public final class PhysicalTenantsITHelper {

  public static final String DEFAULT_TENANT_ID = "default";

  private final Map<String, Storage> tenants;

  private PhysicalTenantsITHelper(final Map<String, Storage> tenants) {
    this.tenants = Collections.unmodifiableMap(new LinkedHashMap<>(tenants));
  }

  public static Builder builder() {
    return new Builder();
  }

  public TestStandaloneBroker configure(final TestStandaloneBroker broker) {
    tenants.forEach(
        (tenant, storage) -> {
          storage.applyTo(broker, tenant);
          if (!DEFAULT_TENANT_ID.equals(tenant)) {
            broker.withPtConfig(
                tenant,
                camunda ->
                    camunda
                        .getSecurity()
                        .getInitialization()
                        .setDefaultRoles(
                            Map.of("admin", Map.of("users", List.of(tenant + "-admin")))));
          }
        });
    return broker;
  }

  public CamundaClientBuilder newClientBuilder(
      final TestGateway<?> gateway, final String tenantId) {
    final CamundaClientBuilder builder = gateway.newClientBuilder();
    if (!DEFAULT_TENANT_ID.equals(tenantId)) {
      // gRPC is scoped via the header; REST is scoped by pointing the client at the tenant-prefixed
      // root (the client appends /v2). Workaround until the client applies physicalTenantId to
      // REST.
      builder.physicalTenantId(tenantId).restAddress(restRootFor(gateway, tenantId));
    }
    return builder;
  }

  public URI restBaseFor(final TestGateway<?> gateway, final String tenantId) {
    return URI.create(restRootFor(gateway, tenantId) + "/v2");
  }

  private static URI restRootFor(final TestGateway<?> gateway, final String tenantId) {
    final String base = gateway.restAddress().toString().replaceAll("/+$", "");
    final String tenantPrefix =
        DEFAULT_TENANT_ID.equals(tenantId) ? "" : "/physical-tenants/" + tenantId;
    return URI.create(base + tenantPrefix);
  }

  public Set<String> tenantIds() {
    return new LinkedHashSet<>(tenants.keySet());
  }

  public static final class Builder {

    private final Map<String, Storage> tenants = new LinkedHashMap<>();

    private Builder() {}

    public Builder withTenant(final String tenantId, final Storage storage) {
      if (tenantId == null || tenantId.isBlank()) {
        throw new IllegalArgumentException("tenantId must not be null or blank");
      }
      Objects.requireNonNull(storage, "storage must not be null for tenant '" + tenantId + "'");
      if (tenants.putIfAbsent(tenantId, storage) != null) {
        throw new IllegalArgumentException(
            "physical tenant '" + tenantId + "' is already declared");
      }
      return this;
    }

    public PhysicalTenantsITHelper build() {
      // the default tenant carries the broker's root config and is addressed without a header, so
      // it must be declared explicitly (see class javadoc); enforcing it also rejects an empty set
      if (!tenants.containsKey(DEFAULT_TENANT_ID)) {
        throw new IllegalStateException(
            "the '" + DEFAULT_TENANT_ID + "' physical tenant must be declared explicitly");
      }
      return new PhysicalTenantsITHelper(tenants);
    }
  }

  private record NoneStorage() implements Storage {

    @Override
    public void applyTo(final TestStandaloneBroker broker, final String tenantId) {
      if (DEFAULT_TENANT_ID.equals(tenantId)) {
        broker.withSecondaryStorageType(SecondaryStorageType.none);
      } else {
        broker.withPtConfig(
            tenantId,
            camunda -> camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.none));
      }
    }
  }

  private record RdbmsStorage(String url, String username, String password) implements Storage {

    @Override
    public void applyTo(final TestStandaloneBroker broker, final String tenantId) {
      if (DEFAULT_TENANT_ID.equals(tenantId)) {
        broker.withSecondaryStorageType(SecondaryStorageType.rdbms);
        broker.withDataConfig(data -> applyRdbms(data.getSecondaryStorage()));
      } else {
        broker.withPtConfig(
            tenantId, camunda -> applyRdbms(camunda.getData().getSecondaryStorage()));
      }
    }

    private void applyRdbms(final SecondaryStorage secondaryStorage) {
      secondaryStorage.setType(SecondaryStorageType.rdbms);
      final var rdbms = secondaryStorage.getRdbms();
      rdbms.setUrl(url);
      rdbms.setUsername(username);
      rdbms.setPassword(password);
    }
  }

  public interface Storage {

    void applyTo(TestStandaloneBroker broker, String tenantId);

    static Storage none() {
      return new NoneStorage();
    }

    static Storage rdbms(final String url, final String username, final String password) {
      return new RdbmsStorage(url, username, password);
    }

    static Storage rdbmsH2(final String dbName) {
      return rdbms(
          "jdbc:h2:mem:" + dbName + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
          "sa",
          "");
    }
  }
}
