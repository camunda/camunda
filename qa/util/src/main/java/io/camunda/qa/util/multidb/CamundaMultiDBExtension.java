/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static org.assertj.core.api.Fail.fail;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.TestEntityCollector.TestEntityCollection;
import io.camunda.qa.util.multidb.TestEntityConfigurer.ConfigurationTestEntities;
import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.lang.reflect.Field;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.ModifierSupport;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * An extension that is able to detect databases setups, configure {@link TestStandaloneApplication}
 * and run test against such them accordingly.
 *
 * <p>Databases can be set up externally. The detection works based on {@link
 * CamundaMultiDBExtension#PROP_CAMUNDA_IT_DATABASE_TYPE} property, which specifies the type of
 * database. Supported types can be found as part of {@link DatabaseType}.
 *
 * <p>Per default, for example if no property is set, local environment is expected. In a local
 * environment case the extension will bootstrap a database via test containers.
 *
 * <p>For simplicity tests can be annotated with {@link MultiDbTest} or {@link HistoryMultiDbTest}, and all the magic happens inside
 * the extension. It will fallback to {@link TestStandaloneBroker}, to bootstrap the broker (reducing the scope), configure it accordingly to the detected database.
 *
 * <pre>{@code
 * @MultiDbTest
 * final class MyMultiDbTest {
 *
 *   private CamundaClient client;
 *
 *   @Test
 *   void shouldMakeUseOfClient() {
 *     // given
 *     // ... set up
 *
 *     // when
 *     topology = c.newTopologyRequest().send().join();
 *
 *     // then
 *     assertThat(topology.getClusterSize()).isEqualTo(1);
 *   }
 * }</pre>
 *
 * If we need to test history clean up, e.g. process instances or task related data are cleaned up.
 * We can use the {@link HistoryMultiDbTest}, that will communicate to enable the retention.
 *
 * <pre>{@code
 * @HistoryMultiDbTest
 * final class MyHistoryMultiDbTest {
 *
 *   private CamundaClient client;
 *
 *   @Test
 *   void shouldMakeUseOfClient() {
 *     // given
 *     // ... set up
 *
 *     // when
 *     // client complete task - PI completion
 *
 *     // then
 *     // assert data is cleaned up
 *   }
 * }</pre>
 *
 * <p>There are more complex scenarios that might need to start or configure respective TestApplication externally.
 * For such cases the respective {@link TestStandaloneApplication} can be annotated with {@link MultiDbTestApplication}:
 * <pre>{@code
 *    @MultiDbTestApplication
 *    static final TestStandaloneBroker BROKER =
 *        new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();
 * }</pre>
 *
 * This is for example necessary for authentication tests.
 *
 *  <pre>{@code
 *  @MultiDbTest
 *  final class MyAuthMultiDbTest {
 *
 *    @MultiDbTestApplication
 *    static final TestStandaloneBroker BROKER =
 *        new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();
 *
 *    private static final String ADMIN = "admin";
 *
 *    @UserDefinition
 *    private static final User ADMIN_USER =
 *      new User(ADMIN,
 *               "password",
 *               List.of(new Permissions(AUTHORIZATION, PermissionTypeEnum.READ, List.of("*"))));
 *
 *    @Test
 *    void shouldMakeUseOfClient(@Authenticated(ADMIN) final CamundaClient adminClient) {
 *      // given
 *      // ... set up
 *
 *      // when
 *      topology = adminClient.newTopologyRequest().send().join();
 *
 *      // then
 *      assertThat(topology.getClusterSize()).isEqualTo(1);
 *    }
 *  }</pre>
 *
 *  As we can see there are further possibilities with the extension, like defining users with
 *  annotated {@link io.camunda.qa.util.auth.UserDefinition}. This allows the extension to pick up the users, and create
 *  them on client usage. Furthermore, authenticated clients are supported with this as well.
 *
 * The following code will inject a client, as parameter, that is authenticated with the ADMIN
 * user.
 * <pre>{@code
 * @Test
 * void shouldMakeUseOfClient(@Authenticated(ADMIN) final CamundaClient adminClient) {
 * </pre>
 *
 *
 *<p>The extension will take care of the life cycle of the {@link TestStandaloneApplication} per default, which
 * means configuring the detected database (this includes Operate, Tasklist, Broker properties and
 * exporter), starting the application, and tearing down at the end.
 *
 * If this is not wanted, the {@link TestStandaloneApplication} can be annotated with  {@link MultiDbTestApplication}
 * and the respective {@link MultiDbTestApplication#managedLifecycle()} set to false:
 *
 *
 *  <pre>{@code
 *  @MultiDbTest
 *  final class MyAuthMultiDbTest {
 *
 *    @MultiDbTestApplication(managedLifecycle = false)
 *    static final TestStandaloneBroker BROKER =
 *        new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();
 *
 *    private static final String ADMIN = "admin";
 *
 *    @UserDefinition
 *    private static final User ADMIN_USER =
 *      new User(ADMIN,
 *               "password",
 *               List.of(new Permissions(AUTHORIZATION, PermissionTypeEnum.READ, List.of("*"))));
 *
 *   @BeforeAll
 *   static void setup() {
 *     // start BROKER separate
 *   }
 *
 *    @Test
 *    void shouldMakeUseOfClient(@Authenticated(ADMIN) final CamundaClient adminClient) {
 *      // given
 *      // ... set up
 *
 *      // when
 *      topology = adminClient.newTopologyRequest().send().join();
 *
 *      // then
 *      assertThat(topology.getClusterSize()).isEqualTo(1);
 *    }
 *  }</pre>
 *
 * <p>See {@link TestStandaloneApplication} for more details.
 */
public class CamundaMultiDBExtension
    implements AfterAllCallback, BeforeAllCallback, ParameterResolver {
  public static final String PROP_CAMUNDA_IT_DATABASE_TYPE =
      "test.integration.camunda.database.type";
  public static final String TEST_INTEGRATION_OPENSEARCH_AWS_URL =
      "test.integration.opensearch.aws.url";
  public static final String PROP_TEST_INTEGRATION_OPENSEARCH_AWS_TIMEOUT =
      "test.integration.opensearch.aws.timeout.seconds";
  public static final String TEST_INTEGRATION_AURORA_AWS_URL = "test.integration.aurora.aws.url";
  public static final String TEST_INTEGRATION_AURORA_AWS_USERNAME =
      "test.integration.aurora.aws.username";
  public static final String TEST_INTEGRATION_AURORA_AWS_PASSWORD =
      "test.integration.aurora.aws.password";
  public static final String TEST_INTEGRATION_RDBMS_FAST_INIT =
      "test.integration.camunda.database.fast-init";
  public static final String TEST_INTEGRATION_PHYSICAL_TENANT =
      "test.integration.camunda.physical-tenant";
  public static final Duration TIMEOUT_DATA_AVAILABILITY =
      Optional.ofNullable(System.getProperty(PROP_TEST_INTEGRATION_OPENSEARCH_AWS_TIMEOUT))
          .map(val -> Duration.ofSeconds(Long.parseLong(val)))
          .orElse(Duration.ofMinutes(2));
  public static final String DEFAULT_ES_URL = "http://localhost:9200";
  public static final String DEFAULT_OS_URL = "http://localhost:9200";
  public static final String DEFAULT_OS_ADMIN_USER = "admin";
  public static final String DEFAULT_OS_ADMIN_PW = "yourStrongPassword123!";
  public static final Duration TIMEOUT_DATABASE_EXPORTER_READINESS = Duration.ofMinutes(3);
  public static final Duration TIMEOUT_DATABASE_READINESS = Duration.ofMinutes(3);
  public static final String KEYCLOAK_REALM = "camunda";
  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaMultiDBExtension.class);
  private static final String EXTENSION_COORDINATION_KEY = "extension-running";
  private static final String EXTENSION_NAME = "CamundaMultiDBExtension";
  private static final String PREFERRED_EXTENSION_PROPERTY = "camunda.test.preferred.extension";
  private static final String PREFERRED_EXTENSION_MULTIDB = "multi-db";
  private static final String PT_ADMIN_PASSWORD = "ptadmin";

  /**
   * Physical-tenant IDs are embedded into SQL identifiers (schema/database/user) and into the
   * per-PT namespace {@code <basePrefix>_<tenantId>}. Restricting them to lowercase alphanumeric
   * (starting with a letter) keeps them valid, unambiguous identifiers on every supported dialect
   * and rules out DDL injection via the bootstrap statements.
   */
  private static final Pattern PHYSICAL_TENANT_ID_PATTERN = Pattern.compile("[a-z][a-z0-9]*");

  /**
   * Maximum physical-tenant id length. The provisioned namespace is {@code <basePrefix>_<tenantId>}
   * where {@code basePrefix} is the 10-character run token; Oracle caps identifiers at 30
   * characters, leaving {@code 30 - 10 - 1 = 19} for the tenant id.
   */
  private static final int MAX_PHYSICAL_TENANT_ID_LENGTH = 19;

  private final List<AutoCloseable> closeables = new ArrayList<>();
  private final TestStandaloneApplication<?> defaultTestApplication;

  private DatabaseType databaseType;
  private ApplicationUnderTest applicationUnderTest;
  private String testPrefix;
  private String physicalTenantId;
  // non-null when @MultiDbPhysicalTenants is present; keyed by tenantId
  private List<String> multiPhysicalTenantIds;
  private MultiPhysicalTenantClients multiPhysicalTenantClients;
  private MultiDbConfigurator multiDbConfigurator;
  private MultiDbSetupHelper setupHelper = new NoopDBSetupHelper();
  private CamundaClientTestFactory authenticatedClientFactory;
  private KeycloakContainer keycloakContainer;

  public CamundaMultiDBExtension() {
    this(new TestStandaloneBroker());
  }

  public CamundaMultiDBExtension(final TestStandaloneApplication<?> testApplication) {
    defaultTestApplication = testApplication;
  }

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

  private DatabaseType getDatabaseType(final ExtensionContext extensionContext) {
    if (databaseType == null) {
      databaseType = currentMultiDbDatabaseType(extensionContext);
    }

    return databaseType;
  }

  public static String getPhysicalTenant() {
    final String value = System.getProperty(TEST_INTEGRATION_PHYSICAL_TENANT);
    return value == null || value.isBlank() ? null : value;
  }

  private DatabaseType currentMultiDbDatabaseType(final ExtensionContext context) {
    final String property =
        System.getProperty(CamundaMultiDBExtension.PROP_CAMUNDA_IT_DATABASE_TYPE);
    if (property != null) {
      return DatabaseType.valueOf(property.toUpperCase());
    }

    final Class<?> testClass = context.getRequiredTestClass();
    return AnnotationSupport.findAnnotation(testClass, HistoryMultiDbTest.class)
        .map(HistoryMultiDbTest::value)
        .or(
            () ->
                AnnotationSupport.findAnnotation(testClass, MultiDbTest.class)
                    .map(MultiDbTest::value))
        .orElse(DatabaseType.LOCAL);
  }

  private void configurePhysicalTenant() {
    // Single-PT path: copy root defaultRoles as-is, no per-PT admin user seeded.
    provisionPhysicalTenant(physicalTenantId, false);
  }

  private void configurePhysicalTenants(final List<String> tenantIds) {
    // Multi-PT path: each PT gets its own seeded <ptId>-admin user + admin default role.
    for (final String tenantId : tenantIds) {
      provisionPhysicalTenant(tenantId, true);
    }
  }

  /**
   * Validates the {@link MultiDbPhysicalTenants} ids: at least one, none blank, no duplicates, and
   * never {@code default} (the default physical tenant is implicit and carries the broker's root
   * config).
   */
  private static List<String> validatedPhysicalTenantIds(final String[] values) {
    final List<String> ids = List.of(values);
    if (ids.isEmpty()) {
      throw new IllegalStateException(
          "@MultiDbPhysicalTenants must declare at least one tenant id");
    }
    final List<String> seen = new ArrayList<>();
    for (final String id : ids) {
      if (id.isBlank()) {
        throw new IllegalStateException("@MultiDbPhysicalTenants tenant ids must not be blank");
      }
      if ("default".equals(id)) {
        throw new IllegalStateException(
            "@MultiDbPhysicalTenants must not include 'default'; the default physical tenant is"
                + " implicit");
      }
      if (!PHYSICAL_TENANT_ID_PATTERN.matcher(id).matches()) {
        throw new IllegalStateException(
            "@MultiDbPhysicalTenants tenant id '"
                + id
                + "' must be lowercase alphanumeric and start with a letter (it is embedded into"
                + " SQL identifiers)");
      }
      if (id.length() > MAX_PHYSICAL_TENANT_ID_LENGTH) {
        throw new IllegalStateException(
            "@MultiDbPhysicalTenants tenant id '"
                + id
                + "' exceeds the maximum length of "
                + MAX_PHYSICAL_TENANT_ID_LENGTH
                + " characters (to keep the provisioned namespace within Oracle's 30-character"
                + " identifier limit)");
      }
      if (seen.contains(id)) {
        throw new IllegalStateException(
            "@MultiDbPhysicalTenants has a duplicate tenant id '" + id + "'");
      }
      seen.add(id);
    }
    return ids;
  }

  /**
   * Provisions a single physical tenant: configures isolated RDBMS secondary storage, and copies
   * the root {@code security.initialization} into the PT — a PT must own its initialization and
   * cannot inherit it from root (see {@code PhysicalTenantRequiredOverrideValidation}).
   * Authentication method and {@code authorizations.enabled} DO inherit from root and are not
   * copied.
   *
   * <p>Storage isolation strategy:
   *
   * <ul>
   *   <li>{@code RDBMS_H2}: each PT gets a fresh dedicated in-memory H2 database (separate URL per
   *       PT, so no table-prefix separation is needed).
   *   <li>other {@code RDBMS_*}: each PT gets a dedicated namespace (schema, database, or user
   *       depending on the dialect) named {@code <basePrefix>_<tenantId>}. The namespace is created
   *       via a bootstrap JDBC connection before the broker starts, and the PT's connection is
   *       pointed at it. The table prefix is left empty because isolation is at the schema level.
   *       <ul>
   *         <li>PostgreSQL / Aurora: {@code CREATE SCHEMA IF NOT EXISTS}; PT URL gets {@code
   *             currentSchema=<ns>} appended.
   *         <li>MySQL / MariaDB: {@code CREATE DATABASE IF NOT EXISTS}; PT URL database path
   *             segment is replaced.
   *         <li>Oracle: a dedicated user/schema ({@code CREATE USER … IDENTIFIED BY …}); PT
   *             username is set to the namespace name.
   *         <li>SQL Server: {@code CREATE DATABASE [<ns>]}; PT URL {@code databaseName} property is
   *             replaced.
   *       </ul>
   * </ul>
   *
   * <p>When {@code seedAdminUser} is {@code true} (multi-PT mode), a {@code <tenantId>-admin}
   * basic-auth user is appended to the PT's init and the {@code admin} default role is bound to
   * that user alone. The PT intentionally does NOT inherit the root/default-tenant default-role
   * bindings (e.g. mapping rules bound to {@code admin}): each physical tenant is isolated and
   * seeds its own admin identity. When {@code false} (single-PT mode), the root {@code
   * defaultRoles} are copied verbatim.
   */
  private void provisionPhysicalTenant(final String tenantId, final boolean seedAdminUser) {
    if (!(applicationUnderTest.application
        instanceof final TestSpringApplication<?> springApplication)) {
      throw new IllegalStateException(
          "Physical-tenant mode requires a TestSpringApplication-based application; got "
              + applicationUnderTest.application.getClass().getName());
    }
    final var rootInit = springApplication.unifiedConfig().getSecurity().getInitialization();
    final String adminUsername = tenantId + "-admin";

    // Derive per-PT storage config based on the active database type.
    final String ptUrl;
    final String ptUsername;
    final String ptPassword;
    final String ptPrefix;
    if (databaseType == DatabaseType.RDBMS_H2) {
      // Fresh in-memory database per PT — DBs are already separate, so no prefix needed.
      ptUrl =
          "jdbc:h2:mem:"
              + testPrefix
              + "-"
              + tenantId
              + "-"
              + UUID.randomUUID()
              + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
      ptUsername = "sa";
      ptPassword = "";
      ptPrefix = testPrefix;
    } else {
      // Dedicated schema per PT: create the namespace via a bootstrap connection, then point the
      // PT's connection at it. Table prefix is empty — isolation is by schema, not prefix.
      final var baseRdbms =
          springApplication.unifiedConfig().getData().getSecondaryStorage().getRdbms();
      final var ptConfig =
          PhysicalTenantSchemaProvisioner.provisionAndDerive(
              databaseType,
              baseRdbms.getUrl(),
              baseRdbms.getUsername(),
              baseRdbms.getPassword(),
              baseRdbms.getPrefix(),
              tenantId);
      ptUrl = ptConfig.url();
      ptUsername = ptConfig.username();
      ptPassword = ptConfig.password();
      ptPrefix = "";
    }

    springApplication.withPtConfig(
        tenantId,
        camunda -> {
          final var secondaryStorage = camunda.getData().getSecondaryStorage();
          secondaryStorage.setType(SecondaryStorageType.rdbms);
          final var rdbms = secondaryStorage.getRdbms();
          rdbms.setUrl(ptUrl);
          rdbms.setUsername(ptUsername);
          rdbms.setPassword(ptPassword);
          rdbms.setPrefix(ptPrefix);

          final var init = camunda.getSecurity().getInitialization();
          init.setUsers(rootInit.getUsers());
          init.setRoles(rootInit.getRoles());
          init.setMappingRules(rootInit.getMappingRules());
          init.setGroups(rootInit.getGroups());
          init.setAuthorizations(rootInit.getAuthorizations());
          init.setTenants(rootInit.getTenants());

          if (seedAdminUser) {
            final var users = new ArrayList<>(init.getUsers());
            users.add(
                new ConfiguredUser(
                    adminUsername,
                    PT_ADMIN_PASSWORD,
                    adminUsername,
                    adminUsername + "@example.com"));
            init.setUsers(users);
            // Bind the admin role to this PT's own admin only — intentionally NOT inheriting the
            // root/default-tenant default-role bindings, so a physical tenant stays isolated.
            init.setDefaultRoles(Map.of("admin", Map.of("users", List.of(adminUsername))));
          } else {
            init.setDefaultRoles(rootInit.getDefaultRoles());
          }
        });
  }

  private MultiPhysicalTenantClients buildMultiPhysicalTenantClients(final List<String> tenantIds) {
    final Map<String, CamundaClient> clients = new LinkedHashMap<>();
    for (final String tenantId : tenantIds) {
      final String adminUsername = tenantId + "-admin";
      final String base =
          applicationUnderTest.application.restAddress().toString().replaceAll("/+$", "");
      final URI restAddress = URI.create(base + "/physical-tenants/" + tenantId);
      final CamundaClient client =
          applicationUnderTest
              .application
              .newClientBuilder()
              .physicalTenantId(tenantId)
              .preferRestOverGrpc(true)
              .restAddress(restAddress)
              .grpcAddress(applicationUnderTest.application.grpcAddress())
              .credentialsProvider(
                  new BasicAuthCredentialsProviderBuilder()
                      .applyEnvironmentOverrides(false)
                      .username(adminUsername)
                      .password(PT_ADMIN_PASSWORD)
                      .build())
              .build();
      clients.put(tenantId, client);
    }
    return new MultiPhysicalTenantClients(clients);
  }

  private void awaitMultiPhysicalTenantAdminsReady(final MultiPhysicalTenantClients ptClients) {
    for (final String tenantId : multiPhysicalTenantIds) {
      final CamundaClient admin = ptClients.admin(tenantId);
      Awaitility.await("per-PT admin ready for tenant " + tenantId)
          .timeout(Duration.ofSeconds(60))
          .ignoreExceptions()
          .untilAsserted(
              () ->
                  org.assertj.core.api.Assertions.assertThat(
                          admin.newUsersSearchRequest().send().join().items())
                      .isNotNull());
    }
  }

  private void injectStaticMultiPtClientsField(
      final Class<?> testClass, final MultiPhysicalTenantClients ptClients) {
    for (final Field field : testClass.getDeclaredFields()) {
      try {
        if (field.getType() == MultiPhysicalTenantClients.class) {
          if (ModifierSupport.isStatic(field)) {
            field.setAccessible(true);
            field.set(null, ptClients);
          } else {
            fail("MultiPhysicalTenantClients field couldn't be injected. Make sure it is static.");
          }
        }
      } catch (final Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  private CamundaClientBuilder applyPhysicalTenant(final CamundaClientBuilder builder) {
    return physicalTenantId != null ? builder.physicalTenantId(physicalTenantId) : builder;
  }

  private URI tenantRestAddress(final URI restAddress) {
    if (physicalTenantId == null) {
      return restAddress;
    }
    final String base = restAddress.toString().replaceAll("/+$", "");
    return URI.create(base + "/physical-tenants/" + physicalTenantId);
  }

  private void setupTestApplication(final Class<?> testClass) {
    applicationUnderTest = getApplicationUnderTest(testClass, ModifierSupport::isStatic);

    final var application = applicationUnderTest.application();
    multiDbConfigurator = new MultiDbConfigurator(application);
    application
        .withUnifiedConfig(
            cfg -> {
              cfg.getData().getPrimaryStorage().setDirectory(DataCfg.DEFAULT_DIRECTORY);
            })
        .withExporter(
            "recordingExporter", cfg -> cfg.setClassName(RecordingExporter.class.getName()));
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    final Class<?> testClass = context.getRequiredTestClass();

    final String preferredExtension = System.getProperty(PREFERRED_EXTENSION_PROPERTY);
    if (preferredExtension != null
        && !preferredExtension.isBlank()
        && !PREFERRED_EXTENSION_MULTIDB.equals(preferredExtension)) {
      LOGGER.info(
          "Skipping CamundaMultiDBExtension - preferred extension is {}", preferredExtension);
      return;
    }

    final var store = coordinationStore(context);

    // Check if another extension has already initialized
    final var extensionRunning = store.get(EXTENSION_COORDINATION_KEY, String.class);
    if (extensionRunning != null) {
      LOGGER.info("Skipping CamundaMultiDBExtension - {} is already running", extensionRunning);
      return;
    }

    // Mark this extension as running
    store.put(EXTENSION_COORDINATION_KEY, EXTENSION_NAME);

    final var databaseType = getDatabaseType(context);
    physicalTenantId = getPhysicalTenant();
    if (physicalTenantId == null) {
      physicalTenantId =
          AnnotationSupport.findAnnotation(testClass, MultiDbTest.class)
              .map(MultiDbTest::physicalTenantId)
              .filter(id -> !id.isBlank())
              .orElse(null);
    }
    if (physicalTenantId != null && !databaseType.storageType().isRdbms()) {
      throw new IllegalStateException(
          "Physical-tenant mode (%s) is only supported on RDBMS storage; got %s."
              .formatted(TEST_INTEGRATION_PHYSICAL_TENANT, databaseType));
    }

    // Multi-PT mode: @MultiDbPhysicalTenants overrides the single-PT path
    final MultiDbPhysicalTenants multiPtAnnotation =
        AnnotationSupport.findAnnotation(testClass, MultiDbPhysicalTenants.class).orElse(null);
    if (multiPtAnnotation != null) {
      if (!databaseType.storageType().isRdbms()) {
        throw new IllegalStateException(
            "@MultiDbPhysicalTenants is only supported on RDBMS storage; got " + databaseType);
      }
      multiPhysicalTenantIds = validatedPhysicalTenantIds(multiPtAnnotation.value());
      // In multi-PT mode the single-PT path is inactive
      physicalTenantId = null;
    }
    LOGGER.info("Starting up Camunda instance, with {}", databaseType);
    final var isHistoryRelatedTest = testClass.isAnnotationPresent(HistoryMultiDbTest.class);
    testPrefix = testClass.getSimpleName().toLowerCase();
    RecordingExporter.reset();
    setupTestApplication(testClass);
    switch (databaseType) {
      case LOCAL -> {
        final ElasticsearchContainer elasticsearchContainer = setupElasticsearch();
        final String elasticSearchUrl = "http://" + elasticsearchContainer.getHttpHostAddress();
        multiDbConfigurator.configureElasticsearchSupport(
            elasticSearchUrl, testPrefix, isHistoryRelatedTest);
        final var expectedDescriptors = new IndexDescriptors(testPrefix, true).all();
        setupHelper = new ElasticsearchSetupHelper(elasticSearchUrl, expectedDescriptors);
      }
      case ES -> {
        multiDbConfigurator.configureElasticsearchSupport(
            DEFAULT_ES_URL, testPrefix, isHistoryRelatedTest);
        final var expectedDescriptors = new IndexDescriptors(testPrefix, true).all();
        setupHelper = new ElasticsearchSetupHelper(DEFAULT_ES_URL, expectedDescriptors);
      }
      case OS -> {
        multiDbConfigurator.configureOpenSearchSupport(
            DEFAULT_OS_URL,
            testPrefix,
            DEFAULT_OS_ADMIN_USER,
            DEFAULT_OS_ADMIN_PW,
            isHistoryRelatedTest,
            false);
        final var expectedDescriptors = new IndexDescriptors(testPrefix, false).all();
        setupHelper = new OpenSearchSetupHelper(DEFAULT_OS_URL, expectedDescriptors);
      }
      case RDBMS_H2 ->
          multiDbConfigurator.configureRDBMSSupport(
              isHistoryRelatedTest,
              "jdbc:h2:mem:testdb+" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
              "sa",
              "");
      case RDBMS_POSTGRES ->
          multiDbConfigurator.configureRDBMSSupport(
              isHistoryRelatedTest, "jdbc:postgresql:camunda", "camunda", "camunda");
      case RDBMS_MARIADB ->
          multiDbConfigurator.configureRDBMSSupport(
              isHistoryRelatedTest, "jdbc:mariadb://localhost:3306/camunda", "camunda", "camunda");
      case RDBMS_MYSQL ->
          multiDbConfigurator.configureRDBMSSupport(
              isHistoryRelatedTest, "jdbc:mysql://localhost:3306/camunda", "camunda", "camunda");
      case RDBMS_ORACLE ->
          multiDbConfigurator.configureRDBMSSupport(
              isHistoryRelatedTest,
              "jdbc:oracle:thin:@localhost:1521/FREEPDB1",
              "camunda",
              "camunda");
      case RDBMS_MSSQL ->
          multiDbConfigurator.configureRDBMSSupport(
              isHistoryRelatedTest,
              "jdbc:sqlserver://localhost:1433;Encrypt=false",
              "sa",
              "Camunda#8_demo");
      case RDBMS_AURORA ->
          multiDbConfigurator.configureRDBMSSupport(
              isHistoryRelatedTest,
              System.getProperty(TEST_INTEGRATION_AURORA_AWS_URL),
              System.getProperty(TEST_INTEGRATION_AURORA_AWS_USERNAME),
              System.getProperty(TEST_INTEGRATION_AURORA_AWS_PASSWORD));
      case AWS_OS -> {
        final var awsOSUrl = System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL);
        multiDbConfigurator.configureAWSOpenSearchSupport(
            awsOSUrl, testPrefix, isHistoryRelatedTest);
        final var expectedDescriptors = new IndexDescriptors(testPrefix, false).all();
        setupHelper = new AWSOpenSearchSetupHelper(awsOSUrl, expectedDescriptors);
      }
      default -> throw new RuntimeException("Database type not supported: " + databaseType);
    }

    if (isHistoryRelatedTest) {
      // make sure history clean up policies are applied more often
      switch (getDatabaseType(context)) {
        case ES, LOCAL:
          setupHelper.applyIndexPoliciesPollInterval(Duration.ofSeconds(1));
          break;
        case OS:
          setupHelper.applyIndexPoliciesPollInterval(
              Duration.ofMinutes(1)); // OpenSearch can't go lower
          break;
        default:
          break;
      }
    }

    // cleaning up secondary storage is explicitly handled in afterAll() method

    Awaitility.await("Await secondary storage connection")
        .timeout(TIMEOUT_DATABASE_READINESS)
        .until(setupHelper::validateConnection);

    final var shouldSetupKeycloak =
        Optional.ofNullable(testClass.getAnnotation(MultiDbTest.class))
            .map(MultiDbTest::setupKeycloak)
            .orElse(false);
    if (shouldSetupKeycloak) {
      setupKeycloak();
      final var issuerUri =
          keycloakContainer.getAuthServerUrl()
              + "/realms/"
              + CamundaMultiDBExtension.KEYCLOAK_REALM;
      applicationUnderTest
          .application()
          .withSecurityConfig(
              c -> {
                c.getAuthentication().getOidc().setClientId("example");
                c.getAuthentication().getOidc().setRedirectUri("example.com");
                c.getAuthentication().getOidc().setIssuerUri(issuerUri);
              });
    }

    if (shouldSetupKeycloak) {
      authenticatedClientFactory =
          new OidcCamundaClientTestFactory(
              applyPhysicalTenant(applicationUnderTest.application.newClientBuilder()),
              tenantRestAddress(applicationUnderTest.application.restAddress()),
              applicationUnderTest.application.grpcAddress(),
              testPrefix,
              keycloakContainer.getAuthServerUrl()
                  + "/realms/camunda/protocol/openid-connect/token");
      injectStaticKeycloakContainerField(testClass, keycloakContainer);
    } else {
      authenticatedClientFactory =
          new BasicAuthCamundaClientTestFactory(
              applyPhysicalTenant(applicationUnderTest.application.newClientBuilder()),
              tenantRestAddress(applicationUnderTest.application.restAddress()),
              applicationUnderTest.application.grpcAddress());
    }

    final TestEntityCollection testEntities = new TestEntityCollector().collect(testClass);
    final ConfigurationTestEntities configuredEntities =
        new TestEntityConfigurer().configure(testEntities);
    applicationUnderTest.application.withSecurityConfig(
        cfg -> {
          final var config = cfg.getInitialization();
          final var users = new ArrayList<>(config.getUsers());
          users.addAll(configuredEntities.users());
          config.setUsers(users);

          final var roles = new ArrayList<>(config.getRoles());
          roles.addAll(configuredEntities.roles());
          config.setRoles(roles);

          final var mappingRules = new ArrayList<>(config.getMappingRules());
          mappingRules.addAll(configuredEntities.mappingRules());
          config.setMappingRules(mappingRules);

          final var groups = new ArrayList<>(config.getGroups());
          groups.addAll(configuredEntities.groups());
          config.setGroups(groups);

          final var authorizations = new ArrayList<>(config.getAuthorizations());
          authorizations.addAll(configuredEntities.authorizations());
          config.setAuthorizations(authorizations);

          final var tenants = new ArrayList<>(config.getTenants());
          tenants.addAll(configuredEntities.tenants());
          config.setTenants(tenants);
        });

    if (physicalTenantId != null) {
      configurePhysicalTenant();
    }

    if (multiPhysicalTenantIds != null) {
      configurePhysicalTenants(multiPhysicalTenantIds);
    }

    if (applicationUnderTest.shouldBeManaged) {
      manageApplicationUnderTest();
    }

    if (multiPhysicalTenantIds != null) {
      multiPhysicalTenantClients = buildMultiPhysicalTenantClients(multiPhysicalTenantIds);
      closeables.add(multiPhysicalTenantClients);
      awaitMultiPhysicalTenantAdminsReady(multiPhysicalTenantClients);
      injectStaticMultiPtClientsField(testClass, multiPhysicalTenantClients);
    }

    final EntityManager entityManager =
        new EntityManager(authenticatedClientFactory.getAdminCamundaClient());

    entityManager.await(configuredEntities);

    createClientsForTestEntities(shouldSetupKeycloak, testEntities);

    // we support only static fields for now - to make sure test setups are build in a way
    // such they are reusable and tests methods are not relying on order, etc.
    // We want to run tests in an efficient manner, and reduce setup time
    injectStaticClientField(testClass);
    injectStaticDatabaseTypeField(testClass, context);
  }

  private KeycloakContainer setupKeycloak() {
    keycloakContainer = DefaultTestContainers.createDefaultKeycloak();
    closeables.add(keycloakContainer);
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

    return keycloakContainer;
  }

  private void createClientsForTestEntities(
      final Boolean shouldSetupKeycloak, final TestEntityCollection testEntities) {

    testEntities
        .users()
        .forEach(
            user -> {
              try {
                final var clientFactory =
                    (BasicAuthCamundaClientTestFactory) authenticatedClientFactory;
                clientFactory.createClientForUser(
                    applyPhysicalTenant(applicationUnderTest.application.newClientBuilder()),
                    tenantRestAddress(applicationUnderTest.application.restAddress()),
                    applicationUnderTest.application.grpcAddress(),
                    user);
              } catch (final ClassCastException e) {
                LOGGER.warn(
                    "Could not create client for user, as the application is not configured for basic authentication",
                    e);
              }
            });

    testEntities
        .mappingRules()
        .forEach(
            mappingRule -> {
              try {
                final var clientFactory = (OidcCamundaClientTestFactory) authenticatedClientFactory;
                clientFactory.createClientForMappingRule(
                    applyPhysicalTenant(applicationUnderTest.application.newClientBuilder()),
                    tenantRestAddress(applicationUnderTest.application.restAddress()),
                    applicationUnderTest.application.grpcAddress(),
                    mappingRule);
              } catch (final ClassCastException e) {
                LOGGER.warn(
                    "Could not create client for mapping rule, as the application is not configured for OIDC authentication",
                    e);
              }

              if (shouldSetupKeycloak) {
                setupUserInKeycloak(mappingRule.id(), mappingRule.claimValue());
              }
            });

    testEntities
        .clients()
        .forEach(
            client -> {
              try {
                final var clientFactory = (OidcCamundaClientTestFactory) authenticatedClientFactory;
                clientFactory.createClientForClient(
                    applyPhysicalTenant(applicationUnderTest.application.newClientBuilder()),
                    tenantRestAddress(applicationUnderTest.application.restAddress()),
                    applicationUnderTest.application.grpcAddress(),
                    client);
              } catch (final ClassCastException e) {
                LOGGER.warn(
                    "Could not create client for client, as the application is not configured for OIDC authentication",
                    e);
              }

              if (shouldSetupKeycloak) {
                setupUserInKeycloak(client.clientId(), client.clientId());
              }
            });
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

  private void manageApplicationUnderTest() {
    final var application = applicationUnderTest.application;
    closeables.add(application);
    application.start();
    application.awaitCompleteTopology(
        application.unifiedConfig(), authenticatedClientFactory.getAdminCamundaClient());

    Awaitility.await("Await exporter readiness")
        .timeout(TIMEOUT_DATABASE_EXPORTER_READINESS)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> setupHelper.validateSchemaCreation(testPrefix));
  }

  private ElasticsearchContainer setupElasticsearch() {
    final ElasticsearchContainer elasticsearchContainer =
        TestSearchContainers.createDefaultElasticsearchContainer()
            // We need to configure ILM to run more often, to make sure data is cleaned up earlier
            // Useful for tests where we verify history clean up
            // Default is 10m
            // https://www.elastic.co/guide/en/elasticsearch/reference/current/ilm-settings.html
            .withEnv("indices.lifecycle.poll_interval", "1s");
    elasticsearchContainer.start();
    closeables.add(elasticsearchContainer);
    return elasticsearchContainer;
  }

  private ApplicationUnderTest getApplicationUnderTest(
      final Class<?> testClass, Predicate<Field> predicate) {
    var testStandaloneApplication = defaultTestApplication;
    var shouldBeManaged = true;
    predicate = predicate.and(field -> field.isAnnotationPresent(MultiDbTestApplication.class));
    for (final Field field : testClass.getDeclaredFields()) {
      try {
        if (predicate.test(field)) {
          if (TestStandaloneApplication.class.isAssignableFrom(field.getType())) {
            field.setAccessible(true);
            testStandaloneApplication = (TestStandaloneApplication<?>) field.get(null);
            final MultiDbTestApplication annotation =
                field.getAnnotation(MultiDbTestApplication.class);
            shouldBeManaged = annotation.managedLifecycle();
            field.setAccessible(false);
          } else {
            fail(
                String.format(
                    "Expected to find field annotated with %s from type %s, but found %s. It doesn't apply criteria.",
                    MultiDbTestApplication.class, TestSpringApplication.class, field.getType()));
          }
        }
      } catch (final Exception ex) {
        throw new RuntimeException(
            String.format(
                "Expected to find field annotated with %s from type %s, but fail during that.",
                MultiDbTestApplication.class, TestSpringApplication.class),
            ex);
      }
    }
    return new ApplicationUnderTest(testStandaloneApplication, shouldBeManaged);
  }

  private void injectStaticClientField(final Class<?> testClass) {
    for (final Field field : testClass.getDeclaredFields()) {
      try {
        if (field.getType() == CamundaClient.class) {
          if (ModifierSupport.isStatic(field)) {
            field.setAccessible(true);
            field.set(null, getCamundaClient(field.getAnnotation(Authenticated.class)));
          } else {
            fail("Camunda Client field couldn't be injected. Make sure it is static.");
          }
        }
      } catch (final Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  private void injectStaticDatabaseTypeField(
      final Class<?> testClass, final ExtensionContext context) {
    for (final Field field : testClass.getDeclaredFields()) {
      try {
        if (field.getType() == DatabaseType.class) {
          if (ModifierSupport.isStatic(field)) {
            field.setAccessible(true);
            field.set(null, getDatabaseType(context));
          } else {
            fail("DatabaseType field couldn't be injected. Make sure it is static.");
          }
        }
      } catch (final Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  private void injectStaticKeycloakContainerField(
      final Class<?> testClass, final KeycloakContainer keycloakContainer) {
    for (final Field field : testClass.getDeclaredFields()) {
      try {
        if (field.getType() == KeycloakContainer.class) {
          if (ModifierSupport.isStatic(field)) {
            field.setAccessible(true);
            field.set(null, keycloakContainer);
          } else {
            fail("Keycloak container field couldn't be injected. Make sure it is static.");
          }
        }
      } catch (final Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  @Override
  public void afterAll(final @NonNull ExtensionContext context) throws Exception {
    // Only cleanup if this extension actually ran
    if (!isOwner(context)) {
      LOGGER.debug("Skipping CamundaMultiDBExtension cleanup - extension didn't run");
      return;
    }

    // 1. Stop application and other resources first (forward iteration)
    CloseHelper.quietCloseAll(closeables);
    CloseHelper.quietClose(authenticatedClientFactory);

    // 2. Delete test indices now that the application has stopped writing
    if (testPrefix != null) {
      try {
        setupHelper.cleanup(testPrefix);
      } catch (final Exception e) {
        LOGGER.warn("Failed to cleanup indices with prefix {}", testPrefix, e);
      }
    }
    CloseHelper.quietClose(setupHelper);

    // 3. Reset exporter to make sure it doesn't interfere with other tests
    RecordingExporter.reset();

    coordinationStore(context).remove(EXTENSION_COORDINATION_KEY);
  }

  @Override
  public boolean supportsParameter(
      final @NonNull ParameterContext parameterContext,
      final @NonNull ExtensionContext extensionContext)
      throws ParameterResolutionException {
    if (!isOwner(extensionContext)) {
      return false;
    }
    final Class<?> paramType = parameterContext.getParameter().getType();
    return paramType == CamundaClient.class || paramType == DatabaseType.class;
  }

  @Override
  public Object resolveParameter(
      final @NonNull ParameterContext parameterContext,
      final @NonNull ExtensionContext extensionContext)
      throws ParameterResolutionException {
    if (!isOwner(extensionContext)) {
      throw new ParameterResolutionException(
          "CamundaMultiDBExtension cannot resolve parameter because it did not own the test class");
    }
    final Class<?> paramType = parameterContext.getParameter().getType();
    if (paramType == CamundaClient.class) {
      return getCamundaClient(parameterContext.getParameter().getAnnotation(Authenticated.class));
    }
    if (paramType == DatabaseType.class) {
      return getDatabaseType(extensionContext);
    }
    throw new ParameterResolutionException("Unsupported parameter type: " + paramType);
  }

  private CamundaClient getCamundaClient(final Authenticated authenticated) {
    return authenticatedClientFactory.getCamundaClient(
        applyPhysicalTenant(applicationUnderTest.application.newClientBuilder()),
        tenantRestAddress(applicationUnderTest.application.restAddress()),
        authenticated);
  }

  public record ApplicationUnderTest(
      TestStandaloneApplication<?> application, boolean shouldBeManaged) {}

  private static final class NoopDBSetupHelper implements MultiDbSetupHelper {
    @Override
    public boolean validateConnection() {
      return true;
    }

    @Override
    public void applyIndexPoliciesPollInterval(final Duration pollInterval) {
      // do nothing
    }

    @Override
    public boolean validateSchemaCreation(final String prefix) {
      return true;
    }

    @Override
    public void cleanup(final String prefix) {}

    @Override
    public void close() throws Exception {}
  }

  /**
   * Test database flavors, each mapped to the storage family it exercises. The family
   * (Elasticsearch / OpenSearch / RDBMS) is reused from the production {@link
   * io.camunda.search.connect.configuration.DatabaseType} so capability checks (e.g. RDBMS-only
   * features) don't hard-code flavor lists here.
   */
  public enum DatabaseType {
    LOCAL(io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH),
    ES(io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH),
    OS(io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH),
    RDBMS_H2(io.camunda.search.connect.configuration.DatabaseType.RDBMS),
    RDBMS_MARIADB(io.camunda.search.connect.configuration.DatabaseType.RDBMS),
    RDBMS_MSSQL(io.camunda.search.connect.configuration.DatabaseType.RDBMS),
    RDBMS_MYSQL(io.camunda.search.connect.configuration.DatabaseType.RDBMS),
    RDBMS_ORACLE(io.camunda.search.connect.configuration.DatabaseType.RDBMS),
    RDBMS_POSTGRES(io.camunda.search.connect.configuration.DatabaseType.RDBMS),
    RDBMS_AURORA(io.camunda.search.connect.configuration.DatabaseType.RDBMS),
    AWS_OS(io.camunda.search.connect.configuration.DatabaseType.OPENSEARCH);

    private final io.camunda.search.connect.configuration.DatabaseType storageType;

    DatabaseType(final io.camunda.search.connect.configuration.DatabaseType storageType) {
      this.storageType = storageType;
    }

    public io.camunda.search.connect.configuration.DatabaseType storageType() {
      return storageType;
    }
  }
}
