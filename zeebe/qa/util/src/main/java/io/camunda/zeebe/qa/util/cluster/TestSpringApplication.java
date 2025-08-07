/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static io.camunda.application.commons.search.SearchEngineDatabaseConfiguration.SearchEngineSchemaManagerProperties.CREATE_SCHEMA_PROPERTY;
import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;

import io.camunda.application.MainSupport;
import io.camunda.application.Profile;
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.application.initializers.HealthConfigurationInitializer;
import io.camunda.authentication.config.AuthenticationProperties;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.util.ContextOverrideInitializer;
import io.camunda.zeebe.qa.util.cluster.util.ContextOverrideInitializer.Bean;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.client.ReactorResourceFactory;

public abstract class TestSpringApplication<T extends TestSpringApplication<T>>
    implements TestApplication<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestSpringApplication.class);

  protected ConfigurableApplicationContext springContext;

  private final Class<?>[] springApplications;
  private final Map<String, Bean<?>> beans;
  private final Map<String, Object> propertyOverrides;
  private final Collection<String> additionalProfiles;
  private final Collection<ApplicationContextInitializer> additionalInitializers;
  private final ReactorResourceFactory reactorResourceFactory = new ReactorResourceFactory();

  public TestSpringApplication(final Class<?>... springApplications) {
    this(new HashMap<>(), new HashMap<>(), new ArrayList<>(), springApplications);
  }

  private TestSpringApplication(
      final Map<String, Bean<?>> beans,
      final Map<String, Object> propertyOverrides,
      final Collection<String> additionalProfiles,
      final Class<?>... springApplications) {
    this.springApplications = springApplications;
    this.beans = beans;
    this.propertyOverrides = propertyOverrides;
    additionalInitializers = new ArrayList<>();
    additionalInitializers.add(new ContextOverrideInitializer(beans, propertyOverrides));
    additionalInitializers.add(new HealthConfigurationInitializer());
    this.additionalProfiles = new ArrayList<>(additionalProfiles);
    this.additionalProfiles.add(Profile.TEST.getId());

    // randomize ports to allow multiple concurrent instances
    overridePropertyIfAbsent("server.port", SocketUtil.getNextAddress().getPort());
    overridePropertyIfAbsent("management.server.port", SocketUtil.getNextAddress().getPort());
    overridePropertyIfAbsent("spring.lifecycle.timeout-per-shutdown-phase", "1s");

    // configure each application to use their own resources for the embedded Netty web server,
    // otherwise shutting one down will shut down all embedded servers
    reactorResourceFactory.setUseGlobalResources(false);
    reactorResourceFactory.setShutdownQuietPeriod(Duration.ZERO);
    beans.put(
        "reactorResourceFactory", new Bean<>(reactorResourceFactory, ReactorResourceFactory.class));
  }

  @Override
  public T start() {
    if (!isStarted()) {
      // simulate initialization of singleton bean; since injected singleton are not initialized,
      // but we still want Spring to manage the individual reactor resources. we do this here since
      // this will create the resources required (which are disposed of later in stop)
      reactorResourceFactory.afterPropertiesSet();

      springContext = createSpringBuilder().run(commandLineArgs());

      LOGGER.info("Started TestSpringApplication ...");
      LOGGER.info("-> Server / Rest Port: {}", restPort());
      LOGGER.info("-> Monitoring Port: {}", monitoringPort());
      LOGGER.info("-> Additional Profiles: {}", additionalProfiles);
      LOGGER.info("-> Secondary Database Type: {}", databaseType());
    }

    return self();
  }

  @Override
  public T stop() {
    if (springContext != null) {
      springContext.close();
      springContext = null;
    }

    return self();
  }

  @Override
  public boolean isStarted() {
    return springContext != null && springContext.isActive();
  }

  @Override
  public <V> T withBean(final String qualifier, final V bean, final Class<V> type) {
    beans.put(qualifier, new Bean<>(bean, type));
    return self();
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case REST -> restPort();
      case MONITORING -> monitoringPort();
      default ->
          throw new IllegalArgumentException(
              "No known port %s; must one of MONITORING".formatted(port));
    };
  }

  @Override
  public <V> V bean(final Class<V> type) {
    if (springContext == null) {
      return beans.values().stream()
          .map(Bean::value)
          .filter(type::isInstance)
          .map(type::cast)
          .findFirst()
          .orElse(null);
    }

    return springContext.getBean(type);
  }

  @Override
  public <V> V property(final String property, final Class<V> type, final V fallback) {
    if (springContext == null) {
      //noinspection unchecked
      return (V) propertyOverrides.getOrDefault(property, fallback);
    }

    return springContext.getEnvironment().getProperty(property, type, fallback);
  }

  @Override
  public T withProperty(final String key, final Object value) {
    propertyOverrides.put(key, value);
    return self();
  }

  @Override
  public T withAdditionalProfile(final String profile) {
    additionalProfiles.add(profile);
    return self();
  }

  public T withBasicAuth() {
    withProperty(AuthenticationProperties.METHOD, AuthenticationMethod.BASIC.name());
    withAdditionalProfile(Profile.CONSOLIDATED_AUTH);
    return self();
  }

  public T withAdditionalInitializer(final ApplicationContextInitializer<?> initializer) {
    additionalInitializers.add(initializer);
    return self();
  }

  public T withAuthenticationMethod(final AuthenticationMethod authenticationMethod) {
    return withAdditionalProfile(Profile.CONSOLIDATED_AUTH)
        .withProperty(AuthenticationProperties.METHOD, authenticationMethod.name());
  }

  protected T withUnauthenticatedAccess(final boolean unprotectedApi) {
    return withProperty(AuthenticationProperties.API_UNPROTECTED, unprotectedApi);
  }

  public final T withUnauthenticatedAccess() {
    return withUnauthenticatedAccess(true);
  }

  public final T withAuthenticatedAccess() {
    return withUnauthenticatedAccess(false);
  }

  public T withCreateSchema(final boolean createSchema) {
    return withProperty(CREATE_SCHEMA_PROPERTY, String.valueOf(createSchema));
  }

  /**
   * Sets the broker's working directory, aka its data directory. If a path is given, the broker
   * will not delete it on shutdown.
   *
   * @param directory path to the broker's root data directory
   * @return itself for chaining
   */
  public final T withWorkingDirectory(final Path directory) {
    return withBean(
        "workingDirectory", new WorkingDirectory(directory, false), WorkingDirectory.class);
  }

  /** Returns the broker's working directory if it is already set, or null otherwise. */
  public final Path getWorkingDirectory() {
    return Optional.ofNullable(bean(WorkingDirectory.class))
        .map(WorkingDirectory::path)
        .orElse(null);
  }

  public final Optional<AuthenticationMethod> apiAuthenticationMethod() {
    if (property(AuthenticationProperties.API_UNPROTECTED, Boolean.class, true)) {
      return Optional.empty();
    } else {
      return AuthenticationMethod.parse(
          property(
              AuthenticationProperties.METHOD, String.class, AuthenticationMethod.BASIC.name()));
    }
  }

  /** Returns the command line arguments that will be passed when the application is started. */
  protected String[] commandLineArgs() {
    return new String[0];
  }

  /**
   * Returns a builder which can be modified to enable more profiles, inject beans, etc. Sub-classes
   * can override this to customize the behavior of the test application.
   */
  protected SpringApplicationBuilder createSpringBuilder() {
    return MainSupport.createDefaultApplicationBuilder()
        .bannerMode(Mode.OFF)
        .registerShutdownHook(false)
        .initializers(additionalInitializers.toArray(ApplicationContextInitializer[]::new))
        .profiles(additionalProfiles.toArray(String[]::new))
        .sources(springApplications);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{nodeId = " + nodeId() + "}";
  }

  private void overridePropertyIfAbsent(final String key, final Object value) {
    if (!propertyOverrides.containsKey(key)) {
      propertyOverrides.put(key, value);
    }
  }

  private String databaseType() {
    return property(PROPERTY_CAMUNDA_DATABASE_TYPE, String.class, "es");
  }

  private int monitoringPort() {
    return serverPort("management.server.port");
  }

  private int restPort() {
    return serverPort("server.port");
  }

  private int serverPort(final String property) {
    final Object portProperty;
    if (springContext != null) {
      portProperty = springContext.getEnvironment().getProperty(property);
    } else {
      portProperty = propertyOverrides.get(property);
    }

    if (portProperty == null) {
      throw new IllegalStateException(
          "No property '%s' defined anywhere, cannot infer monitoring port".formatted(property));
    }

    if (portProperty instanceof final Integer port) {
      return port;
    }

    return Integer.parseInt(portProperty.toString());
  }
}
