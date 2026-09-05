/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.configuration.SecondaryStorage;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.awaitility.Awaitility;

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
 *       // both gRPC and REST calls are scoped to tenanta
 *     }
 *   }
 * }
 * }</pre>
 */
public final class PhysicalTenantsITHelper {

  public static final String DEFAULT_TENANT_ID = "default";

  private static final Duration ADMIN_READY_TIMEOUT = Duration.ofSeconds(30);

  private final Map<String, TenantSpec> tenants;

  private PhysicalTenantsITHelper(final Map<String, TenantSpec> tenants) {
    this.tenants = Collections.unmodifiableMap(new LinkedHashMap<>(tenants));
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fully configures the broker for the physical tenants: applies every tenant's static
   * configuration ({@link #configureStatic}) and its secondary storage. Suitable for
   * instance-scoped brokers configured once per test instance. Static brokers reused across runs
   * should instead call {@link #configureStatic} once at setup and {@link #refreshSecondaryStorage}
   * per {@code @BeforeAll}, so storage is applied exactly once per start with a fresh database
   * identity.
   */
  public TestStandaloneBroker configure(final TestStandaloneBroker broker) {
    configureStatic(broker);
    return refreshSecondaryStorage(broker);
  }

  /**
   * Applies every tenant's static configuration: the per-tenant partition count when one was
   * declared via {@link Builder#withTenant(String, Storage, int)}, the {@code admin} default role
   * for each non-default physical tenant's {@code <tenant>-admin} user, and each non-default
   * tenant's exporters-assigned manifest. Does not touch secondary storage, so it is safe to call
   * once at static setup while storage is (re-)applied per run via {@link
   * #refreshSecondaryStorage}.
   */
  public TestStandaloneBroker configureStatic(final TestStandaloneBroker broker) {
    applyPartitionCounts(broker);
    applyAdminRoles(broker);
    applyAssignedExporters(broker);
    return broker;
  }

  private void applyPartitionCounts(final TestStandaloneBroker broker) {
    tenants.forEach(
        (tenant, spec) -> {
          if (spec.partitionCount() == null) {
            return;
          }
          if (DEFAULT_TENANT_ID.equals(tenant)) {
            // the default tenant's partitions are the broker's root cluster configuration
            broker.withClusterConfig(cluster -> cluster.setPartitionCount(spec.partitionCount()));
          } else {
            broker.withPtConfig(
                tenant, camunda -> camunda.getCluster().setPartitionCount(spec.partitionCount()));
          }
        });
  }

  private void applyAdminRoles(final TestStandaloneBroker broker) {
    nonDefaultTenants()
        .forEach(
            tenant ->
                broker.withPtConfig(
                    tenant,
                    camunda ->
                        camunda
                            .getSecurity()
                            .getInitialization()
                            .setDefaultRoles(
                                Map.of("admin", Map.of("users", List.of(adminUsername(tenant)))))));
  }

  /**
   * A non-default physical tenant must explicitly declare which generic exporters run for it
   * ({@code PhysicalTenantExporterAssignedValidation}, ADR-0008 D1/D2/D5) once any generic exporter
   * exists in the root catalog — but leaving the manifest unbound is valid too as long as the
   * tenant's resolved catalog stays empty. {@code TestClusterBuilder} registers recordingExporter
   * by default (and physical-tenant ITs built on it assert on records exported from the tenant's
   * own partitions), so it is assigned here when present; callers that configure their own generic
   * exporters and exporters-assigned afterwards (e.g. {@code PhysicalTenantExporterConfigIT}) are
   * left untouched, since setting an empty manifest here would collide with their later indexed
   * {@code exporters-assigned[n]} properties. A caller that later toggles {@link
   * TestStandaloneBroker#withRecordingExporter} off (e.g. before a restart) must call {@link
   * #refreshExportersAssigned} to keep this from going stale.
   */
  private void applyAssignedExporters(final TestStandaloneBroker broker) {
    if (!broker
        .unifiedConfig()
        .getData()
        .getExporters()
        .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID)) {
      return;
    }
    nonDefaultTenants()
        .forEach(
            tenant ->
                broker.withProperty(
                    "camunda.physical-tenants." + tenant + ".data.exporters-assigned[0]",
                    TestStandaloneBroker.RECORDING_EXPORTER_ID));
  }

  private Stream<String> nonDefaultTenants() {
    return tenants.keySet().stream().filter(tenant -> !DEFAULT_TENANT_ID.equals(tenant));
  }

  /**
   * Re-syncs each non-default physical tenant's {@code exporters-assigned} manifest with whether
   * {@code recordingExporter} is currently present in the broker's root catalog — including
   * clearing a manifest {@link #configureStatic} previously assigned it into. Call this after
   * toggling {@link TestStandaloneBroker#withRecordingExporter} on an already-{@link #configure}d
   * broker (e.g. before a restart that removes the exporter from the root configuration): the
   * manifest {@link #configureStatic} set at initial setup only reflects the catalog as it stood
   * then, and would otherwise keep assigning an id no longer in the root catalog, tripping {@code
   * PhysicalTenantExporterAssignedValidation}'s unknown-id check on the next restart.
   *
   * <p>Unlike {@link #configureStatic}, this always writes something — an empty manifest when
   * {@code recordingExporter} is absent, to actively mask a previously-assigned entry (a bare-key
   * entry takes precedence over indexed entries under Spring's relaxed {@code List<String>}
   * binding). Only call this on a broker that {@link #configureStatic} already set up; calling it
   * standalone on a broker managing its own exporters-assigned (e.g. {@code
   * PhysicalTenantExporterConfigIT}) would collide with its indexed {@code exporters-assigned[n]}
   * properties the same way an unconditional write in {@link #configureStatic} would.
   */
  public TestStandaloneBroker refreshExportersAssigned(final TestStandaloneBroker broker) {
    final boolean recordingExporterPresent =
        broker
            .unifiedConfig()
            .getData()
            .getExporters()
            .containsKey(TestStandaloneBroker.RECORDING_EXPORTER_ID);
    tenants
        .keySet()
        .forEach(
            tenant -> {
              if (DEFAULT_TENANT_ID.equals(tenant)) {
                return;
              }
              final String assignedExportersKey =
                  "camunda.physical-tenants." + tenant + ".data.exporters-assigned";
              if (recordingExporterPresent) {
                broker.withProperty(
                    assignedExportersKey + "[0]", TestStandaloneBroker.RECORDING_EXPORTER_ID);
              } else {
                broker.withProperty(assignedExportersKey, "");
              }
            });
    return broker;
  }

  /**
   * Re-stamps every physical tenant's secondary storage with a fresh database identity. Call in
   * {@code @BeforeAll} before starting the broker so that a failsafe rerun within the same JVM gets
   * isolated databases rather than recovering the previous run's exporter position against a
   * freshly-initialised log. Only the storage is re-applied; default roles and seeded users are
   * left untouched.
   */
  public TestStandaloneBroker refreshSecondaryStorage(final TestStandaloneBroker broker) {
    tenants.forEach((tenant, spec) -> spec.storage().applyTo(broker, tenant));
    return broker;
  }

  /**
   * Seeds a basic-auth per-PT admin {@code <tenantId>-admin} user for a non-{@code default}
   * physical tenant (matching the {@code defaultRoles.admin} assignment {@link #configureStatic}
   * sets) into that tenant's {@code security.initialization}, so the user can authenticate via the
   * tenant-prefixed REST path once authorizations and basic auth are enabled. Basic-auth specific
   * (an OIDC variant would seed a mapping rule instead). Appends the per-PT admin user to the
   * tenant's existing initialization users list; call in addition to {@link #configureStatic}.
   */
  public TestStandaloneBroker seedBasicAuthAdminUser(
      final TestStandaloneBroker broker, final String tenantId, final String password) {
    final String username = adminUsername(tenantId);
    broker.withPtConfig(
        tenantId,
        camunda -> {
          final var init = camunda.getSecurity().getInitialization();
          final var users = new ArrayList<>(init.getUsers());
          users.add(new ConfiguredUser(username, password, username, username + "@example.com"));
          init.setUsers(users);
        });
    return broker;
  }

  /** The conventional admin username for a physical tenant: {@code <tenantId>-admin}. */
  public String adminUsername(final String tenantId) {
    return tenantId + "-admin";
  }

  /**
   * Waits until the given physical-tenant admin client can authenticate, tolerating the transient
   * 401s that occur immediately after startup until the PT's admin user is initialized in its
   * schema. Prefer this over cluster-topology readiness checks for a multi-PT broker, whose
   * partition-id-keyed topology cannot represent the per-PT raft groups.
   */
  public void awaitAdminReady(final CamundaClient admin) {
    Awaitility.await("per-PT admin can authenticate")
        .atMost(ADMIN_READY_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThat(admin.newUsersSearchRequest().send().join().items()).isNotNull());
  }

  /**
   * Builds an authenticated, REST-first client for the basic-auth per-PT admin user (see {@link
   * #seedBasicAuthAdminUser}). The client is scoped to the tenant's REST path and authenticates
   * over basic auth.
   */
  public CamundaClientBuilder newBasicAuthAdminClientBuilder(
      final TestGateway<?> gateway, final String tenantId, final String password) {
    return newClientBuilder(gateway, tenantId)
        .preferRestOverGrpc(true)
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder()
                .applyEnvironmentOverrides(false)
                .username(adminUsername(tenantId))
                .password(password)
                .build());
  }

  public CamundaClientBuilder newClientBuilder(
      final TestGateway<?> gateway, final String tenantId) {
    final CamundaClientBuilder builder = gateway.newClientBuilder();
    if (!DEFAULT_TENANT_ID.equals(tenantId)) {
      // The client scopes both transports to the physical tenant: gRPC via the
      // Camunda-Physical-Tenant header, and REST by auto-prefixing /physical-tenants/<id> onto the
      // base REST address.
      builder.physicalTenantId(tenantId);
    }
    return builder;
  }

  public Set<String> tenantIds() {
    return new LinkedHashSet<>(tenants.keySet());
  }

  private record TenantSpec(Storage storage, Integer partitionCount) {}

  public static final class Builder {

    private final Map<String, TenantSpec> tenants = new LinkedHashMap<>();

    private Builder() {}

    public Builder withTenant(final String tenantId, final Storage storage) {
      return withTenant(tenantId, storage, null);
    }

    /**
     * Declares a physical tenant with an explicit partition count. Giving tenants different
     * partition counts is the configuration that exposes misrouted requests: on a symmetric
     * cluster, a request that reaches the wrong tenant's engine often still finds an
     * identical-looking partition and passes.
     */
    public Builder withTenant(
        final String tenantId, final Storage storage, final int partitionCount) {
      if (partitionCount < 1) {
        throw new IllegalArgumentException(
            "partitionCount of tenant '" + tenantId + "' must be at least 1");
      }
      return withTenant(tenantId, storage, Integer.valueOf(partitionCount));
    }

    private Builder withTenant(
        final String tenantId, final Storage storage, final Integer partitionCount) {
      if (tenantId == null || tenantId.isBlank()) {
        throw new IllegalArgumentException("tenantId must not be null or blank");
      }
      Objects.requireNonNull(storage, "storage must not be null for tenant '" + tenantId + "'");
      if (tenants.putIfAbsent(tenantId, new TenantSpec(storage, partitionCount)) != null) {
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

  private record ElasticsearchStorage(String url, String indexPrefix) implements Storage {

    @Override
    public void applyTo(final TestStandaloneBroker broker, final String tenantId) {
      if (DEFAULT_TENANT_ID.equals(tenantId)) {
        broker.withSecondaryStorageType(SecondaryStorageType.elasticsearch);
        broker.withDataConfig(data -> applyElasticsearch(data.getSecondaryStorage()));
      } else {
        broker.withPtConfig(
            tenantId, camunda -> applyElasticsearch(camunda.getData().getSecondaryStorage()));
      }
    }

    private void applyElasticsearch(final SecondaryStorage secondaryStorage) {
      secondaryStorage.setType(SecondaryStorageType.elasticsearch);
      final var elasticsearch = secondaryStorage.getElasticsearch();
      elasticsearch.setUrl(url);
      elasticsearch.setIndexPrefix(indexPrefix);
    }
  }

  public interface Storage {

    void applyTo(TestStandaloneBroker broker, String tenantId);

    static Storage none() {
      return new NoneStorage();
    }

    static Storage elasticsearch(final String url, final String indexPrefix) {
      return new ElasticsearchStorage(url, indexPrefix);
    }

    static Storage rdbms(final String url, final String username, final String password) {
      return new RdbmsStorage(url, username, password);
    }

    static Storage rdbmsH2(final String dbName) {
      return new RdbmsH2Storage(dbName);
    }
  }

  /**
   * In-memory H2 storage that mints a fresh database identity on every {@link #applyTo} call rather
   * than baking one URL at construction. An in-memory H2 with {@code DB_CLOSE_DELAY=-1} survives
   * for the lifetime of the JVM, so a {@code static} broker reusing a single URL across a failsafe
   * rerun in the same JVM would recover the previous run's exporter position against a
   * freshly-initialised log and fail to recover the exporter. Re-stamping a fresh database per
   * broker start (see {@link #refreshSecondaryStorage}) keeps the secondary storage isolated per
   * run, in lockstep with the per-lifecycle primary storage.
   */
  private record RdbmsH2Storage(String dbName) implements Storage {
    @Override
    public void applyTo(final TestStandaloneBroker broker, final String tenantId) {
      new RdbmsStorage(
              "jdbc:h2:mem:"
                  + dbName
                  + "-"
                  + UUID.randomUUID()
                  + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
              "sa",
              "")
          .applyTo(broker, tenantId);
    }
  }
}
