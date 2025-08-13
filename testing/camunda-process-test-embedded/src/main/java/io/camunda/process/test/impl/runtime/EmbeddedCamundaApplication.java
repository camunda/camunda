/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.runtime;

import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;

import io.camunda.application.MainSupport;
import io.camunda.application.Profile;
import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.application.initializers.HealthConfigurationInitializer;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.authentication.config.AuthenticationProperties;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.identity.IdentityModuleConfiguration;
import io.camunda.operate.OperateModuleConfiguration;
import io.camunda.process.test.impl.runtime.ContextOverrideInitializer.Bean;
import io.camunda.security.configuration.ConfiguredMappingRule;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.security.configuration.InitializationConfiguration;
import io.camunda.tasklist.TasklistModuleConfiguration;
import io.camunda.webapps.WebappsModuleConfiguration;
import io.camunda.zeebe.broker.BrokerModuleConfiguration;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.util.unit.DataSize;

public class EmbeddedCamundaApplication {

  private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedCamundaApplication.class);

  private static final String DEFAULT_MAPPING_RULE_ID = "default";
  private static final String DEFAULT_MAPPING_RULE_CLAIM_NAME = "client_id";
  private static final String DEFAULT_MAPPING_RULE_CLAIM_VALUE = "default";

  protected ConfigurableApplicationContext springContext;

  private final Collection<String> additionalProfiles;

  private final Collection<ApplicationContextInitializer> additionalInitializers;

  private final Map<String, Object> propertyOverrides = new HashMap<>();

  private final Map<String, Bean<?>> beans = new HashMap<>();

  private final ReactorResourceFactory reactorResourceFactory = new ReactorResourceFactory();

  private final Class<?>[] springApplications =
      new Class[] {
        // Unified Configuration classes
        UnifiedConfiguration.class,
        TasklistPropertiesOverride.class,
        OperatePropertiesOverride.class,
        UnifiedConfigurationHelper.class,
        // ---
        CommonsModuleConfiguration.class,
        OperateModuleConfiguration.class,
        TasklistModuleConfiguration.class,
        IdentityModuleConfiguration.class,
        WebappsModuleConfiguration.class,
        BrokerModuleConfiguration.class
      };

  private final BrokerBasedProperties brokerProperties;
  private final CamundaSecurityProperties securityConfig;

  public EmbeddedCamundaApplication() {
    // --------- From TestSpringApplication ---------
    additionalProfiles = new ArrayList<>();

    additionalInitializers = new ArrayList<>();
    additionalInitializers.add(new ContextOverrideInitializer(beans, propertyOverrides));
    additionalInitializers.add(new HealthConfigurationInitializer());

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

    // ------------------- From TestCamundaApplication -------------------
    brokerProperties = new BrokerBasedProperties();

    brokerProperties.getNetwork().getCommandApi().setPort(SocketUtil.getNextAddress().getPort());
    brokerProperties.getNetwork().getInternalApi().setPort(SocketUtil.getNextAddress().getPort());
    brokerProperties.getGateway().getNetwork().setPort(SocketUtil.getNextAddress().getPort());

    // set a smaller default log segment size since we pre-allocate, which might be a lot in tests
    // for local development; also lower the watermarks for local testing
    brokerProperties.getData().setLogSegmentSize(DataSize.ofMegabytes(16));
    brokerProperties.getData().getDisk().getFreeSpace().setProcessing(DataSize.ofMegabytes(128));
    brokerProperties.getData().getDisk().getFreeSpace().setReplication(DataSize.ofMegabytes(64));

    brokerProperties.getExperimental().getConsistencyChecks().setEnableForeignKeyChecks(true);
    brokerProperties.getExperimental().getConsistencyChecks().setEnablePreconditions(true);

    securityConfig = new CamundaSecurityProperties();
    securityConfig.getAuthorizations().setEnabled(false);
    securityConfig.getAuthentication().setUnprotectedApi(true);
    securityConfig
        .getInitialization()
        .getUsers()
        .add(
            new ConfiguredUser(
                InitializationConfiguration.DEFAULT_USER_USERNAME,
                InitializationConfiguration.DEFAULT_USER_PASSWORD,
                InitializationConfiguration.DEFAULT_USER_NAME,
                InitializationConfiguration.DEFAULT_USER_EMAIL));
    securityConfig
        .getInitialization()
        .getMappingRules()
        .add(
            new ConfiguredMappingRule(
                DEFAULT_MAPPING_RULE_ID,
                DEFAULT_MAPPING_RULE_CLAIM_NAME,
                DEFAULT_MAPPING_RULE_CLAIM_VALUE));
    securityConfig
        .getInitialization()
        .getDefaultRoles()
        .put(
            "admin",
            Map.of(
                "users",
                List.of(InitializationConfiguration.DEFAULT_USER_USERNAME),
                "mappingRules",
                List.of(DEFAULT_MAPPING_RULE_ID)));

    //noinspection resource
    withBean("config", brokerProperties, BrokerBasedProperties.class)
        .withBean("security-config", securityConfig, CamundaSecurityProperties.class)
        .withProperty(
            AuthenticationProperties.API_UNPROTECTED,
            securityConfig.getAuthentication().getUnprotectedApi())
        .withProperty(
            "camunda.security.authorizations.enabled",
            securityConfig.getAuthorizations().isEnabled());

    additionalProfiles.add(Profile.BROKER.getId());
    additionalProfiles.add("consolidated-auth");

    additionalInitializers.add(new WebappsConfigurationInitializer());
  }

  private void overridePropertyIfAbsent(final String key, final Object value) {
    if (!propertyOverrides.containsKey(key)) {
      propertyOverrides.put(key, value);
    }
  }

  public void start() {
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

  public void stop() {
    if (springContext != null) {
      springContext.close();
      springContext = null;
    }
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

  /** Returns the command line arguments that will be passed when the application is started. */
  protected String[] commandLineArgs() {
    return new String[0];
  }

  public int restPort() {
    return serverPort("server.port");
  }

  public int monitoringPort() {
    return serverPort("management.server.port");
  }

  public int grpcPort() {
    return brokerProperties.getGateway().getNetwork().getPort();
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

  private String databaseType() {
    return property(PROPERTY_CAMUNDA_DATABASE_TYPE, String.class, "es");
  }

  public <V> V property(final String property, final Class<V> type, final V fallback) {
    if (springContext == null) {
      //noinspection unchecked
      return (V) propertyOverrides.getOrDefault(property, fallback);
    }

    return springContext.getEnvironment().getProperty(property, type, fallback);
  }

  public <V> EmbeddedCamundaApplication withBean(
      final String qualifier, final V bean, final Class<V> type) {
    beans.put(qualifier, new Bean<>(bean, type));
    return this;
  }

  public EmbeddedCamundaApplication withProperty(final String key, final Object value) {
    // Since the security config is not constructed from the properties, we need to manually update
    // it when we override a property.
    AuthenticationProperties.applyToSecurityConfig(securityConfig, key, value);
    propertyOverrides.put(key, value);
    return this;
  }

  public EmbeddedCamundaApplication withExporter(
      final String id, final Consumer<ExporterCfg> modifier) {
    final var cfg = new ExporterCfg();
    modifier.accept(cfg);
    brokerProperties.getExporters().put(id, cfg);
    return this;
  }
}
