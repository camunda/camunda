/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.ConfigurationSecretReference;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class ConfigurationSecretsTest {

  private static final String PASSWORD = "data.secondary-storage.elasticsearch.password";

  @TempDir private Path secrets;

  @Test
  void shouldResolveTheReferencesOfEveryTenantInPlace() throws IOException {
    // given
    writeSecret("root", "es-password", "root-password");
    writeSecret("root", "license", "license-key");
    writeSecret("tenanta", "es-password", "tenanta-password");
    final var environment =
        environment(
            Map.of(
                "camunda.secrets.internal-config.file.path",
                secrets.resolve("root").toString(),
                "camunda." + PASSWORD,
                "secret:es-password",
                "camunda.license.key",
                "secret:license",
                "camunda.physical-tenants.tenanta.secrets.internal-config.file.path",
                secrets.resolve("tenanta").toString()));
    final var camunda = bind(environment);
    final var configurationSecrets = new ConfigurationSecrets(environment);

    // when
    final var resolver = configurationSecrets.resolve(camunda);

    // then
    assertThat(camunda.getData().getSecondaryStorage().getElasticsearch().getPassword())
        .isEqualTo("root-password");
    assertThat(camunda.getLicense().getKey()).isEqualTo("license-key");
    assertThat(
            resolver
                .forPhysicalTenant("tenanta")
                .getData()
                .getSecondaryStorage()
                .getElasticsearch()
                .getPassword())
        .isEqualTo("tenanta-password");
    assertThat(configurationSecrets.references().forPhysicalTenant("tenanta"))
        .containsEntry(PASSWORD, new ConfigurationSecretReference("es-password"));
    assertThat(configurationSecrets.references().forPhysicalTenant("default"))
        .containsEntry(PASSWORD, new ConfigurationSecretReference("es-password"))
        .containsEntry("license.key", new ConfigurationSecretReference("license"));
    assertThat(configurationSecrets.references().forPhysicalTenant("tenanta"))
        .doesNotContainKey("license.key");
    configurationSecrets.stores().close();
  }

  @Test
  void shouldNameTheTenantPropertyAndReferenceThatCantBeResolved() throws IOException {
    // given
    writeSecret("root", "other", "other-value");
    writeSecret("tenanta", "es-password", "tenanta-password");
    final var environment =
        environment(
            Map.of(
                "camunda.secrets.internal-config.file.path",
                secrets.resolve("root").toString(),
                "camunda." + PASSWORD,
                "secret:es-password",
                "camunda.physical-tenants.tenanta.secrets.internal-config.file.path",
                secrets.resolve("tenanta").toString()));
    final var camunda = bind(environment);
    final var configurationSecrets = new ConfigurationSecrets(environment);

    // when / then
    assertThatThrownBy(() -> configurationSecrets.resolve(camunda))
        .hasMessageContaining("Physical tenant 'default'")
        .hasMessageContaining("secret:es-password for " + PASSWORD)
        .hasMessageContaining("NOT_FOUND")
        .hasMessageNotContaining("tenanta-password")
        .hasMessageNotContaining("other-value");
  }

  @Test
  void shouldResolveTheRootConfigurationBeforeAnyOtherBeanReadsIt() throws IOException {
    // given
    writeSecret("root", "es-password", "root-password");
    final var context = new AnnotationConfigApplicationContext();
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.secrets.internal-config.file.path",
                    secrets.resolve("root").toString(),
                    "camunda." + PASSWORD,
                    "secret:es-password")));
    context.register(Application.class);

    // when
    context.refresh();

    // then
    try (context) {
      assertThat(context.getBean(PasswordReader.class).password()).isEqualTo("root-password");
    }
  }

  @Test
  void shouldResolveTheSecurityLibrarysOwnBindingOfTheRootConfiguration() throws IOException {
    // given
    writeSecret("root", "admin-password", "resolved-admin-password");
    final var context = new AnnotationConfigApplicationContext();
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "test",
                Map.of(
                    "camunda.secrets.internal-config.file.path",
                    secrets.resolve("root").toString(),
                    "camunda.security.initialization.users[0].username",
                    "admin",
                    "camunda.security.initialization.users[0].password",
                    "secret:admin-password")));
    context.register(SecurityApplication.class);

    // when
    context.refresh();

    // then
    try (context) {
      assertThat(
              context
                  .getBean(CamundaSecurityLibraryProperties.class)
                  .getInitialization()
                  .getUsers())
          .singleElement()
          .satisfies(user -> assertThat(user.getPassword()).isEqualTo("resolved-admin-password"));
    }
  }

  private void writeSecret(final String store, final String name, final String value)
      throws IOException {
    Files.createDirectories(secrets.resolve(store));
    Files.writeString(secrets.resolve(store).resolve(name), value);
  }

  private static MockEnvironment environment(final Map<String, Object> properties) {
    final var all = new HashMap<>(properties);
    if (properties.keySet().stream().anyMatch(key -> key.contains(".tenanta."))) {
      all.put(
          "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
          "tenanta-admin");
      all.put(
          "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
          "tenanta");
    }
    final var environment = new MockEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", all));
    return environment;
  }

  private static Camunda bind(final Environment environment) {
    final var camunda = new Camunda();
    Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));
    return camunda;
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(Camunda.class)
  static class Application {

    @Bean
    static ConfigurationSecrets configurationSecrets(final Environment environment) {
      return new ConfigurationSecrets(environment);
    }

    @Bean
    PasswordReader passwordReader(final Camunda camunda) {
      return new PasswordReader(
          camunda.getData().getSecondaryStorage().getElasticsearch().getPassword());
    }
  }

  record PasswordReader(String password) {}

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties({Camunda.class, CamundaSecurityLibraryProperties.class})
  static class SecurityApplication {

    @Bean
    static ConfigurationSecrets configurationSecrets(final Environment environment) {
      return new ConfigurationSecrets(environment);
    }
  }
}
