/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretStoreRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

final class InternalConfigSecretsTest {

  private static final String DEFAULT = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final SecretStoreConfiguration CONFIG = new SecretStoreConfiguration();

  @TempDir private Path root;

  @AutoClose private InternalConfigSecrets internalConfigSecrets;
  @AutoClose private SecretStoreRegistries jobRegistries;

  @Test
  void shouldResolveAConfigurationReferenceFromTheInternalConfigStore() throws IOException {
    // given
    final var stores = fileStores("db-password", "s3cret");
    build(stores.properties());

    // when
    final var resolved =
        internalConfigSecrets
            .forPhysicalTenant(DEFAULT)
            .resolve(
                Map.of(
                    "password", "camunda.secrets.db-password",
                    "username", "camunda"));

    // then
    assertThat(resolved).containsEntry("password", "s3cret").containsEntry("username", "camunda");
  }

  @Test
  void shouldNotResolveAnInternalConfigSecretFromAJob() throws IOException {
    // given
    final var stores = fileStores("db-password", "s3cret");
    build(stores.properties());

    // when
    final var result =
        jobRegistries
            .forPhysicalTenant(DEFAULT)
            .getStores()
            .get(SecretStoreRegistry.DEFAULT_STORE_ID)
            .resolveFromStore(Set.of("db-password"));

    // then
    assertThat(result.get("db-password"))
        .isInstanceOfSatisfying(
            Failed.class, failed -> assertThat(failed.code()).isEqualTo(SecretErrorCode.NOT_FOUND));
  }

  @Test
  void shouldNotResolveAJobSecretFromAConfigurationReference() throws IOException {
    // given
    final var stores = fileStores("db-password", "s3cret");
    Files.writeString(stores.jobs().resolve("api-token"), "job-value");
    build(stores.properties());
    final var resolver = internalConfigSecrets.forPhysicalTenant(DEFAULT);

    // when / then
    assertThatThrownBy(() -> resolver.resolve(Map.of("password", "camunda.secrets.api-token")))
        .isInstanceOf(InternalConfigResolutionException.class)
        .hasMessageContaining("password (api-token: NOT_FOUND)");
  }

  @Test
  void shouldRejectAReferenceWhenTheTenantHasNoInternalConfigStore() throws IOException {
    // given
    final var jobs = Files.createDirectory(root.resolve("jobs"));
    build(Map.of("camunda.secrets.stores.file.default.path", jobs.toString()));
    final var resolver = internalConfigSecrets.forPhysicalTenant(DEFAULT);

    // when / then
    assertThatThrownBy(() -> resolver.resolve(Map.of("password", "camunda.secrets.db-password")))
        .isInstanceOf(InternalConfigResolutionException.class)
        .hasMessageContaining("Physical tenant 'default'")
        .hasMessageContaining("[password]")
        .hasMessageContaining("no internal-config secret store");
  }

  @Test
  void shouldNeverNameTheValueOfAResolvedSecret() throws IOException {
    // given
    final var stores = fileStores("db-password", "s3cret");
    build(stores.properties());
    final var resolver = internalConfigSecrets.forPhysicalTenant(DEFAULT);

    // when / then
    assertThatThrownBy(
            () ->
                resolver.resolve(
                    Map.of(
                        "password", "camunda.secrets.db-password",
                        "url", "camunda.secrets.missing")))
        .hasMessageContaining("url (missing: NOT_FOUND)")
        .hasMessageNotContaining("s3cret");
  }

  @Test
  void shouldGiveEveryPhysicalTenantItsOwnInternalConfigStore() throws IOException {
    // given
    final var defaultStores = fileStores("db-password", "default-value");
    final var tenantJobs = Files.createDirectory(root.resolve("tenanta-jobs"));
    final var tenantInternal = Files.createDirectory(root.resolve("tenanta-internal"));
    Files.writeString(tenantInternal.resolve("db-password"), "tenanta-value");
    final var properties = new HashMap<>(defaultStores.properties());
    properties.putAll(
        Map.of(
            "camunda.physical-tenants.tenanta.secrets.stores.file.default.path",
            tenantJobs.toString(),
            "camunda.physical-tenants.tenanta.secrets.stores.file.default.internal-config.path",
            tenantInternal.toString(),
            "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
            "tenanta-admin",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
            "tenanta"));
    build(properties);
    final var reference = Map.of("password", "camunda.secrets.db-password");

    // when
    final var defaultValue = internalConfigSecrets.forPhysicalTenant(DEFAULT).resolve(reference);
    final var tenantValue = internalConfigSecrets.forPhysicalTenant("tenanta").resolve(reference);

    // then
    assertThat(defaultValue).containsEntry("password", "default-value");
    assertThat(tenantValue).containsEntry("password", "tenanta-value");
  }

  @Test
  void shouldRefuseToStartWhenTheInternalConfigScopeIsTheJobScope() throws IOException {
    // given
    final var jobs = Files.createDirectory(root.resolve("jobs"));
    final var properties =
        Map.<String, Object>of(
            "camunda.secrets.stores.file.default.path", jobs.toString(),
            "camunda.secrets.stores.file.default.internal-config.path", jobs.toString());

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(() -> build(properties))
        .withMessageContaining("Physical tenant 'default'")
        .withMessageContaining(
            "camunda.secrets.stores.file.default.internal-config.path ('%s')".formatted(jobs))
        .withMessageContaining("camunda.secrets.stores.file.default.path ('%s')".formatted(jobs));
  }

  @Test
  void shouldRefuseToStartWhenTheInternalConfigScopeIsWithinTheJobScope() throws IOException {
    // given
    final var jobs = Files.createDirectory(root.resolve("jobs"));
    final var internal = Files.createDirectory(jobs.resolve("internal"));
    final var properties =
        Map.<String, Object>of(
            "camunda.secrets.stores.file.default.path", jobs.toString(),
            "camunda.secrets.stores.file.default.internal-config.path", internal.toString());

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(() -> build(properties))
        .withMessageContaining("is within camunda.secrets.stores.file.default.path");
  }

  @Test
  void shouldRefuseToStartWhenACloudInternalConfigScopeIsReachableByJobs() {
    // given
    final var properties =
        Map.<String, Object>of(
            "camunda.secrets.stores.aws.default.region", "eu-west-1",
            "camunda.secrets.stores.aws.default.path-prefix", "camunda/",
            "camunda.secrets.stores.aws.default.internal-config.path-prefix", "camunda/");

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(() -> build(properties))
        .withMessageContaining(
            "camunda.secrets.stores.aws.default.internal-config.path-prefix ('camunda/') is within"
                + " camunda.secrets.stores.aws.default.path-prefix ('camunda/')");
  }

  @Test
  void shouldNameThePhysicalTenantPropertiesOfAScopeClash() throws IOException {
    // given
    final var jobs = Files.createDirectory(root.resolve("jobs"));
    final var properties =
        Map.<String, Object>of(
            "camunda.physical-tenants.tenanta.secrets.stores.file.default.path",
            jobs.toString(),
            "camunda.physical-tenants.tenanta.secrets.stores.file.default.internal-config.path",
            jobs.toString(),
            "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
            "tenanta-admin",
            "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
            "tenanta");

    // when / then
    assertThatIllegalStateException()
        .isThrownBy(() -> build(properties))
        .withMessageContaining("Physical tenant 'tenanta'")
        .withMessageContaining(
            "camunda.physical-tenants.tenanta.secrets.stores.file.default.internal-config.path");
  }

  private FileStores fileStores(final String name, final String value) throws IOException {
    final var jobs = Files.createDirectory(root.resolve("jobs"));
    final var internal = Files.createDirectory(root.resolve("internal"));
    Files.writeString(internal.resolve(name), value);
    return new FileStores(jobs, internal);
  }

  private void build(final Map<String, Object> properties) {
    final var resolver = resolverFor(properties);
    jobRegistries =
        CONFIG.secretStoreRegistries(
            resolver, System::currentTimeMillis, new SimpleMeterRegistry());
    internalConfigSecrets = CONFIG.internalConfigSecrets(resolver);
  }

  private static PhysicalTenantResolver resolverFor(final Map<String, Object> properties) {
    final var env = new MockEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("test", properties));
    final var camunda = new Camunda();
    Binder.get(env).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return PhysicalTenantResolver.of(env, camunda);
  }

  private record FileStores(Path jobs, Path internal) {

    private Map<String, Object> properties() {
      return Map.of(
          "camunda.secrets.stores.file.default.path", jobs.toString(),
          "camunda.secrets.stores.file.default.internal-config.path", internal.toString());
    }
  }
}
