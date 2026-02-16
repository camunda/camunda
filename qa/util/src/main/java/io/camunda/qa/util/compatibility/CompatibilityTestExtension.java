/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.compatibility;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.KEYCLOAK_REALM;
import static org.assertj.core.api.Fail.fail;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.ClientDefinition;
import io.camunda.qa.util.auth.GroupDefinition;
import io.camunda.qa.util.auth.MappingRuleDefinition;
import io.camunda.qa.util.auth.RoleDefinition;
import io.camunda.qa.util.auth.TestClient;
import io.camunda.qa.util.auth.TestGroup;
import io.camunda.qa.util.auth.TestMappingRule;
import io.camunda.qa.util.auth.TestRole;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.BasicAuthCamundaClientTestFactory;
import io.camunda.qa.util.multidb.CamundaClientTestFactory;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.qa.util.multidb.EntityManager;
import io.camunda.qa.util.multidb.OidcCamundaClientTestFactory;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.platform.commons.support.ModifierSupport;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

/** JUnit extension that manages the lifecycle of a versioned Camunda container */
public class CompatibilityTestExtension
    implements BeforeAllCallback,
        AfterAllCallback,
        BeforeEachCallback,
        ParameterResolver,
        TestExecutionExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(CompatibilityTestExtension.class);
  private static final String EXTENSION_COORDINATION_KEY = "extension-running";
  private static final String EXTENSION_NAME = "CompatibilityTestExtension";
  private static final String PREFERRED_EXTENSION_PROPERTY = "camunda.test.preferred.extension";
  private static final String PREFERRED_EXTENSION_COMPATIBILITY = "compatibility";
  private static final int GRPC_PORT = 26500;
  private static final int REST_PORT = 8080;
  private static final int MANAGEMENT_PORT = 9600;

  private Network network;
  private ElasticsearchContainer elasticsearchContainer;
  private GenericContainer<?> camundaContainer;
  private CamundaClientTestFactory authenticatedClientFactory;
  private EntityManager entityManager;
  private DatabaseType databaseType;
  private String testPrefix;
  private KeycloakContainer keycloakContainer;
  private Throwable incompatibilityError;

  private static ExtensionContext.Store coordinationStore(final ExtensionContext context) {
    final Class<?> testClass = context.getRequiredTestClass();
    return context
        .getRoot()
        .getStore(ExtensionContext.Namespace.create("test-extension-coordination", testClass));
  }

  private static boolean isOwner(final ExtensionContext context) {
    final String extensionRunning =
        coordinationStore(context).get(EXTENSION_COORDINATION_KEY, String.class);
    return EXTENSION_NAME.equals(extensionRunning);
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    final Class<?> testClass = context.getRequiredTestClass();

    final String preferredExtension = System.getProperty(PREFERRED_EXTENSION_PROPERTY);
    if (preferredExtension != null
        && !preferredExtension.isBlank()
        && !PREFERRED_EXTENSION_COMPATIBILITY.equals(preferredExtension)) {
      LOGGER.info(
          "Skipping CompatibilityTestExtension - preferred extension is {}", preferredExtension);
      return;
    }

    final var store = coordinationStore(context);

    // Check if another extension has already initialized
    final var extensionRunning = store.get(EXTENSION_COORDINATION_KEY, String.class);
    if (extensionRunning != null) {
      LOGGER.info("Skipping CompatibilityTestExtension - {} is already running", extensionRunning);
      return;
    }

    // Mark this extension as running
    store.put(EXTENSION_COORDINATION_KEY, EXTENSION_NAME);

    final CompatibilityTest annotation = testClass.getAnnotation(CompatibilityTest.class);

    if (annotation == null) {
      throw new IllegalStateException(
          "@CompatibilityTest annotation not found on test class: " + testClass.getName());
    }

    // Determine version and database type
    final String version =
        System.getProperty("camunda.compatibility.test.version", annotation.version());
    databaseType = DatabaseType.ES;
    testPrefix = testClass.getSimpleName().toLowerCase();

    LOGGER.info(
        "Starting Camunda container - version: {}, database: {}, testPrefix: {}",
        version,
        databaseType,
        testPrefix);

    // Create network for containers to communicate
    network = Network.newNetwork();

    // Start database container
    startDatabase();

    // Start Camunda container
    startCamundaContainer(version, annotation);

    // Wait for Camunda to be fully ready
    waitForCamundaReadiness();

    // Create default client
    if (annotation.setupKeycloak()) {
      authenticatedClientFactory =
          new OidcCamundaClientTestFactory(
              CamundaClient.newClientBuilder(),
              getRestAddress(),
              getGrpcAddress(),
              testPrefix,
              keycloakContainer.getAuthServerUrl()
                  + "/realms/camunda/protocol/openid-connect/token");
      injectStaticKeycloakContainerField(testClass, keycloakContainer);
    } else {
      authenticatedClientFactory =
          new BasicAuthCamundaClientTestFactory(
              CamundaClient.newClientBuilder(), getRestAddress(), getGrpcAddress());
    }
    // Setup entities
    entityManager = new EntityManager(authenticatedClientFactory.getAdminCamundaClient());
    try {
      setupEntities(testClass, annotation.setupKeycloak());
    } catch (final NoClassDefFoundError | IncompatibleClassChangeError e) {
      LOGGER.warn(
          "Client API incompatibility detected during entity setup: {} - {}",
          e.getClass().getSimpleName(),
          e.getMessage());
      incompatibilityError = e;
    }

    // Inject static client field
    injectStaticClientField(testClass);

    LOGGER.info("Camunda container started successfully with {} database", databaseType);
  }

  private void startDatabase() {
    switch (databaseType) {
      case ES -> startElasticsearch();
      default -> throw new IllegalArgumentException("Unsupported database type: " + databaseType);
    }
  }

  private void startElasticsearch() {
    LOGGER.info("Starting Elasticsearch container");

    elasticsearchContainer =
        TestSearchContainers.createDefeaultElasticsearchContainer()
            .withNetwork(network)
            .withNetworkAliases("elasticsearch")
            .withEnv("indices.lifecycle.poll_interval", "1s");

    elasticsearchContainer.start();

    LOGGER.info("Elasticsearch started at: {}", elasticsearchContainer.getHttpHostAddress());
  }

  private void startCamundaContainer(final String version, final CompatibilityTest annotation) {
    final String imageName =
        "SNAPSHOT".equals(version) ? "camunda/camunda:SNAPSHOT" : "camunda/camunda:" + version;

    LOGGER.info("Starting Camunda container with image: {}", imageName);

    camundaContainer =
        new GenericContainer<>(DockerImageName.parse(imageName))
            .withNetwork(network)
            .withExposedPorts(GRPC_PORT, REST_PORT, MANAGEMENT_PORT)
            .withStartupTimeout(Duration.ofMinutes(5))
            .waitingFor(
                new HttpWaitStrategy()
                    .forPort(MANAGEMENT_PORT)
                    .forPath("/actuator/health/readiness")
                    .withStartupTimeout(Duration.ofMinutes(5)));

    // Configure database connection using the configurator
    final String databaseUrl = getDatabaseUrl();
    final CompatibilityTestDatabaseConfigurator dbConfigurator =
        new CompatibilityTestDatabaseConfigurator(testPrefix, databaseType, databaseUrl);

    dbConfigurator.configureCamundaContainer(camundaContainer);

    // Enable Spring profiles
    camundaContainer.withEnv("SPRING_PROFILES_ACTIVE", "broker,consolidated-auth");

    // Configure authentication
    if (annotation.enableAuthorization()) {
      camundaContainer.withEnv("CAMUNDA_SECURITY_AUTHENTICATION_METHOD", "basic"); // lowercase
      camundaContainer.withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "false");
      camundaContainer.withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "true");
    } else {
      camundaContainer.withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "true");
      camundaContainer.withEnv("CAMUNDA_SECURITY_AUTHORIZATIONS_ENABLED", "false");
    }

    if (annotation.enableMultiTenancy()) {
      camundaContainer.withEnv("CAMUNDA_SECURITY_MULTITENANCY_CHECKSENABLED", "true");
      camundaContainer.withEnv("CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI", "false");
    }

    // Apply additional environment variables from annotation
    for (final String envVar : annotation.envVars()) {
      final String[] parts = envVar.split("=", 2);
      if (parts.length == 2) {
        final String key = parts[0].trim();
        final String value = parts[1].trim();
        camundaContainer.withEnv(key, value);
        LOGGER.debug("Setting custom environment variable: {}={}", key, value);
      } else {
        LOGGER.warn("Invalid environment variable format (expected KEY=VALUE): {}", envVar);
      }
    }

    // Configure initial user (required for basic auth)
    camundaContainer.withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME", "demo");
    camundaContainer.withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD", "demo");
    camundaContainer.withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME", "Demo");
    camundaContainer.withEnv("CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL", "demo@example.com");
    // IMPORTANT: Assign demo user to admin role so it can create other entities
    camundaContainer.withEnv("CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0", "demo");

    // Configure security based on annotation
    if (annotation.setupKeycloak()) {
      setupKeycloak();
      camundaContainer.withEnv(
          "CAMUNDA_SECURITY_AUTHENTICATION_OIDC_ISSUER_URI",
          keycloakContainer.getAuthServerUrl() + "/realms/" + KEYCLOAK_REALM);
      camundaContainer.withEnv("CAMUNDA_SECURITY_AUTHENTICATION_OIDC_CLIENT_ID", "example");
      camundaContainer.withEnv("CAMUNDA_SECURITY_AUTHENTICATION_OIDC_REDIRECT_URI", "example.com");
    }

    camundaContainer.start();

    LOGGER.info(
        "Camunda container started - GRPC: {}, REST: {}, Management: {}",
        getGrpcAddress(),
        getRestAddress(),
        getManagementAddress());
  }

  private void setupKeycloak() {
    keycloakContainer = DefaultTestContainers.createDefaultKeycloak();
    keycloakContainer.start();

    final var realm = new RealmRepresentation();
    realm.setRealm(KEYCLOAK_REALM);
    realm.setEnabled(true);
    try (final var keycloak = keycloakContainer.getKeycloakAdminClient()) {
      keycloak.realms().create(realm);
    }
    setupUserInKeycloak(
        TestStandaloneBroker.DEFAULT_MAPPING_RULE_ID,
        TestStandaloneBroker.DEFAULT_MAPPING_RULE_CLAIM_VALUE);
  }

  private void setupUserInKeycloak(final String mappingRuleId, final String claimValue) {
    final var clientRepresentation = new ClientRepresentation();
    clientRepresentation.setClientId(claimValue);
    clientRepresentation.setEnabled(true);
    clientRepresentation.setClientAuthenticatorType("client-secret");
    clientRepresentation.setSecret(claimValue);
    clientRepresentation.setServiceAccountsEnabled(true);

    final var userRepresentation = new UserRepresentation();
    userRepresentation.setId(claimValue);
    userRepresentation.setUsername(claimValue);
    userRepresentation.setServiceAccountClientId(mappingRuleId);
    userRepresentation.setEnabled(true);

    try (final var keycloak = keycloakContainer.getKeycloakAdminClient()) {
      final var realm = keycloak.realm(CamundaMultiDBExtension.KEYCLOAK_REALM);
      realm.clients().create(clientRepresentation).close();
      realm.users().create(userRepresentation).close();
    }
  }

  private String getDatabaseUrl() {
    switch (databaseType) {
      case LOCAL, ES:
        // Use network alias for container-to-container communication
        return "http://elasticsearch:9200";
      default:
        throw new IllegalArgumentException("Unsupported database type: " + databaseType);
    }
  }

  private void waitForCamundaReadiness() {
    LOGGER.info(
        "Waiting for Camunda to be fully ready (exporter, indexing, and user initialization)");

    // Wait for the demo user to be created and authentication to work
    Awaitility.await("Await Camunda readiness with authenticated access")
        .timeout(CamundaMultiDBExtension.TIMEOUT_DATABASE_EXPORTER_READINESS)
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              // Try to create an authenticated client and make a simple request
              final var testClient =
                  CamundaClient.newClientBuilder()
                      .grpcAddress(getGrpcAddress())
                      .restAddress(getRestAddress())
                      .credentialsProvider(
                          new BasicAuthCredentialsProviderBuilder()
                              .username("demo")
                              .password("demo")
                              .build())
                      .build();

              try {
                // Try a simple request that requires auth
                testClient.newTopologyRequest().send().join();
                LOGGER.debug("Camunda is ready and demo user can authenticate");
              } finally {
                testClient.close();
              }
            });

    LOGGER.info("Camunda is ready");
  }

  private URI getGrpcAddress() {
    return URI.create(
        "http://" + camundaContainer.getHost() + ":" + camundaContainer.getMappedPort(GRPC_PORT));
  }

  private URI getRestAddress() {
    return URI.create(
        "http://" + camundaContainer.getHost() + ":" + camundaContainer.getMappedPort(REST_PORT));
  }

  private URI getManagementAddress() {
    return URI.create(
        "http://"
            + camundaContainer.getHost()
            + ":"
            + camundaContainer.getMappedPort(MANAGEMENT_PORT));
  }

  private void setupEntities(final Class<?> testClass, final boolean setupKeycloak) {
    LOGGER.info("Setting up entities from annotations");

    // Find all entity definitions
    final List<TestUser> users = findUsers(testClass);
    final List<TestGroup> groups = findGroups(testClass);
    final List<TestRole> roles = findRoles(testClass);
    final List<TestMappingRule> mappingRules = findMappingRules(testClass);
    final List<TestClient> clients = findClients(testClass);

    // Create entities
    entityManager
        .withUser(users)
        .withClients(clients)
        .withMappingRules(mappingRules)
        .withGroups(groups)
        .withRoles(roles)
        .await();

    // Create authenticated clients for users
    users.forEach(
        user -> {
          final var clientFactory = (BasicAuthCamundaClientTestFactory) authenticatedClientFactory;
          clientFactory.createClientForUser(
              CamundaClient.newClientBuilder(), getRestAddress(), getGrpcAddress(), user);
          LOGGER.debug("Created authenticated client for user: {}", user.username());
        });

    mappingRules.forEach(
        mappingRule -> {
          try {
            final var clientFactory = (OidcCamundaClientTestFactory) authenticatedClientFactory;
            clientFactory.createClientForMappingRule(
                CamundaClient.newClientBuilder(), getRestAddress(), getGrpcAddress(), mappingRule);
          } catch (final ClassCastException e) {
            LOGGER.warn(
                "Could not create client for mapping rule, as the application is not configured for OIDC authentication",
                e);
          }

          if (setupKeycloak) {
            setupUserInKeycloak(mappingRule.id(), mappingRule.claimValue());
          }
        });

    clients.forEach(
        client -> {
          try {
            final var clientFactory = (OidcCamundaClientTestFactory) authenticatedClientFactory;
            clientFactory.createClientForClient(
                CamundaClient.newClientBuilder(), getRestAddress(), getGrpcAddress(), client);
          } catch (final ClassCastException e) {
            LOGGER.warn(
                "Could not create client for client, as the application is not configured for OIDC authentication",
                e);
          }

          if (setupKeycloak) {
            setupUserInKeycloak(client.clientId(), client.clientId());
          }
        });

    LOGGER.info(
        "Entities created: {} users, {} groups, {} roles, {} mapping rules, {} clients",
        users.size(),
        groups.size(),
        roles.size(),
        mappingRules.size(),
        clients.size());
  }

  private List<TestUser> findUsers(final Class<?> testClass) {
    return findFields(testClass, TestUser.class, UserDefinition.class);
  }

  private List<TestGroup> findGroups(final Class<?> testClass) {
    return findFields(testClass, TestGroup.class, GroupDefinition.class);
  }

  private List<TestRole> findRoles(final Class<?> testClass) {
    return findFields(testClass, TestRole.class, RoleDefinition.class);
  }

  private List<TestMappingRule> findMappingRules(final Class<?> testClass) {
    return findFields(testClass, TestMappingRule.class, MappingRuleDefinition.class);
  }

  private List<TestClient> findClients(final Class<?> testClass) {
    return findFields(testClass, TestClient.class, ClientDefinition.class);
  }

  private <T> List<T> findFields(
      final Class<?> testClass,
      final Class<T> entityClass,
      final Class<? extends Annotation> definitionClass) {

    final List<T> instances = new ArrayList<>();
    final Predicate<Field> predicate =
        field ->
            ModifierSupport.isStatic(field)
                && field.getType() == entityClass
                && field.getAnnotation(definitionClass) != null;

    Class<?> current = testClass;
    while (current != null && current != Object.class) {
      for (final Field field : current.getDeclaredFields()) {
        try {
          if (predicate.test(field)) {
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            final T instance = (T) field.get(null);
            instances.add(instance);
          }
        } catch (final Exception ex) {
          LOGGER.error("Failed to extract field: " + field.getName(), ex);
          throw new RuntimeException(ex);
        }
      }
      current = current.getSuperclass();
    }

    return instances;
  }

  private void injectStaticClientField(final Class<?> testClass) {
    Class<?> current = testClass;
    while (current != null && current != Object.class) {
      for (final Field field : current.getDeclaredFields()) {
        try {
          if (field.getType() == CamundaClient.class && ModifierSupport.isStatic(field)) {
            field.setAccessible(true);
            final Authenticated authenticated = field.getAnnotation(Authenticated.class);
            final CamundaClient client = getCamundaClient(authenticated);
            field.set(null, client);
            LOGGER.debug("Injected CamundaClient into static field: {}", field.getName());
            return;
          }
        } catch (final Exception ex) {
          LOGGER.error("Failed to inject client field: " + field.getName(), ex);
          throw new RuntimeException(ex);
        }
      }
      current = current.getSuperclass();
    }
  }

  private void injectStaticKeycloakContainerField(
      final Class<?> testClass, final KeycloakContainer keycloakContainer) {

    Class<?> current = testClass;

    while (current != null && current != Object.class) {
      for (final Field field : current.getDeclaredFields()) {
        try {
          if (field.getType() == KeycloakContainer.class) {
            if (ModifierSupport.isStatic(field)) {
              field.setAccessible(true);
              field.set(null, keycloakContainer);
              return;
            } else {
              fail(
                  "Keycloak container field couldn't be injected. Make sure it is static: "
                      + field);
            }
          }
        } catch (final Exception ex) {
          throw new RuntimeException(ex);
        }
      }

      current = current.getSuperclass();
    }
  }

  private CamundaClient getCamundaClient(final Authenticated authenticated) {
    return authenticatedClientFactory.getCamundaClient(
        CamundaClient.newClientBuilder(), getRestAddress(), authenticated);
  }

  @Override
  public void beforeEach(final ExtensionContext context) {
    if (!isOwner(context)) {
      return;
    }
    if (incompatibilityError != null) {
      Assumptions.abort(
          "Test skipped due to client API incompatibility during setup: "
              + incompatibilityError.getMessage());
    }
  }

  @Override
  public void handleTestExecutionException(
      final ExtensionContext context, final Throwable throwable) throws Throwable {
    if (!isOwner(context)) {
      throw throwable;
    }

    // Check if the root cause is a linkage error indicating client API incompatibility
    final Throwable rootCause = getRootCause(throwable);
    if (rootCause instanceof NoSuchMethodError
        || rootCause instanceof NoClassDefFoundError
        || rootCause instanceof IncompatibleClassChangeError) {
      LOGGER.warn(
          "Skipping test due to client API incompatibility: {} - {}",
          rootCause.getClass().getSimpleName(),
          rootCause.getMessage());
      Assumptions.abort(
          "Test skipped due to client API incompatibility: " + rootCause.getMessage());
    }

    throw throwable;
  }

  private static Throwable getRootCause(final Throwable throwable) {
    Throwable cause = throwable;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause;
  }

  @Override
  public void afterAll(final ExtensionContext context) {
    if (!isOwner(context)) {
      LOGGER.debug("Skipping CompatibilityTestExtension cleanup - extension didn't run");
      return;
    }

    LOGGER.info("Stopping Camunda and database containers");
    CloseHelper.quietClose(authenticatedClientFactory);
    CloseHelper.quietClose(camundaContainer);
    CloseHelper.quietClose(elasticsearchContainer);
    CloseHelper.quietClose(network);
    CloseHelper.quietClose(keycloakContainer);

    coordinationStore(context).remove(EXTENSION_COORDINATION_KEY);
  }

  @Override
  public boolean supportsParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return isOwner(extensionContext)
        && parameterContext.getParameter().getType() == CamundaClient.class;
  }

  @Override
  public Object resolveParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    if (!isOwner(extensionContext)) {
      throw new ParameterResolutionException(
          "CompatibilityTestExtension cannot resolve parameter because it did not own the test class");
    }
    final Authenticated authenticated =
        parameterContext.getParameter().getAnnotation(Authenticated.class);
    return getCamundaClient(authenticated);
  }
}
