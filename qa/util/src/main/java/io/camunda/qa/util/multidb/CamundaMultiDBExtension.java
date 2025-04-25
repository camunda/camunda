/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import static org.assertj.core.api.Fail.fail;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.User;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.ModifierSupport;
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
 *  annotated {@link UserDefinition}. This allows the extension to pick up the users, and create
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
  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaMultiDBExtension.class);
  private final DatabaseType databaseType;
  private final List<AutoCloseable> closeables = new ArrayList<>();
  private final TestStandaloneApplication<?> defaultTestApplication;

  private ApplicationUnderTest applicationUnderTest;
  private String testPrefix;
  private MultiDbConfigurator multiDbConfigurator;
  private MultiDbSetupHelper setupHelper = new NoopDBSetupHelper();
  private CamundaClientTestFactory authenticatedClientFactory;

  public CamundaMultiDBExtension() {
    this(new TestStandaloneBroker());
  }

  public CamundaMultiDBExtension(final TestStandaloneApplication testApplication) {
    defaultTestApplication = testApplication;
    // resolve active database and exporter type
    databaseType = currentMultiDbDatabaseType();
  }

  public static DatabaseType currentMultiDbDatabaseType() {
    final String property =
        System.getProperty(CamundaMultiDBExtension.PROP_CAMUNDA_IT_DATABASE_TYPE);
    return property == null ? DatabaseType.LOCAL : DatabaseType.valueOf(property.toUpperCase());
  }

  private void setupTestApplication(final Class<?> testClass) {
    applicationUnderTest = getApplicationUnderTest(testClass, ModifierSupport::isStatic);

    final var application = applicationUnderTest.application();
    multiDbConfigurator = new MultiDbConfigurator(application);
    application
        .withBrokerConfig(cfg -> cfg.getGateway().setEnable(true))
        .withExporter(
            "recordingExporter", cfg -> cfg.setClassName(RecordingExporter.class.getName()));
  }

  @Override
  public void beforeAll(final ExtensionContext context) {
    LOGGER.info("Starting up Camunda instance, with {}", databaseType);
    final Class<?> testClass = context.getRequiredTestClass();
    final var isHistoryRelatedTest = testClass.isAnnotationPresent(HistoryMultiDbTest.class);
    testPrefix = testClass.getSimpleName().toLowerCase();

    setupTestApplication(testClass);
    switch (databaseType) {
      case LOCAL -> {
        final ElasticsearchContainer elasticsearchContainer = setupElasticsearch();
        final String elasticSearchUrl = "http://" + elasticsearchContainer.getHttpHostAddress();
        multiDbConfigurator.configureElasticsearchSupport(
            elasticSearchUrl, testPrefix, isHistoryRelatedTest);
        final var expectedDescriptors = new IndexDescriptors(testPrefix, true).all();
        setupHelper = new ElasticOpenSearchSetupHelper(elasticSearchUrl, expectedDescriptors);
      }
      case ES -> {
        multiDbConfigurator.configureElasticsearchSupport(
            DEFAULT_ES_URL, testPrefix, isHistoryRelatedTest);
        final var expectedDescriptors = new IndexDescriptors(testPrefix, true).all();
        setupHelper = new ElasticOpenSearchSetupHelper(DEFAULT_ES_URL, expectedDescriptors);
      }
      case OS -> {
        multiDbConfigurator.configureOpenSearchSupport(
            DEFAULT_OS_URL,
            testPrefix,
            DEFAULT_OS_ADMIN_USER,
            DEFAULT_OS_ADMIN_PW,
            isHistoryRelatedTest);
        final var expectedDescriptors = new IndexDescriptors(testPrefix, false).all();
        setupHelper = new ElasticOpenSearchSetupHelper(DEFAULT_OS_URL, expectedDescriptors);
      }
      case RDBMS -> multiDbConfigurator.configureRDBMSSupport(isHistoryRelatedTest);
      case AWS_OS -> {
        final var awsOSUrl = System.getProperty(TEST_INTEGRATION_OPENSEARCH_AWS_URL);
        multiDbConfigurator.configureAWSOpenSearchSupport(awsOSUrl, testPrefix);
        final var expectedDescriptors = new IndexDescriptors(testPrefix, false).all();
        setupHelper = new AWSOpenSearchSetupHelper(awsOSUrl, expectedDescriptors);
      }
      default -> throw new RuntimeException("Unknown exporter type");
    }

    // we need to close the test application before cleaning up
    closeables.add(() -> setupHelper.cleanup(testPrefix));
    closeables.add(setupHelper);

    Awaitility.await("Await secondary storage connection")
        .timeout(TIMEOUT_DATABASE_READINESS)
        .until(setupHelper::validateConnection);

    if (applicationUnderTest.shouldBeManaged) {
      manageApplicationUnderTest();
    }

    authenticatedClientFactory = new CamundaClientTestFactory();
    // we support only static fields for now - to make sure test setups are build in a way
    // such they are reusable and tests methods are not relying on order, etc.
    // We want to run tests in a efficient manner, and reduce setup time
    injectStaticClientField(testClass);
    final List<User> users = findUsers(testClass, null, ModifierSupport::isStatic);
    if (!users.isEmpty()) {
      authenticatedClientFactory.withUsers(users);
    }
  }

  private void manageApplicationUnderTest() {
    final var application = applicationUnderTest.application;
    closeables.add(application);
    application.start();
    application.awaitCompleteTopology();

    Awaitility.await("Await exporter readiness")
        .timeout(TIMEOUT_DATABASE_EXPORTER_READINESS)
        .pollInterval(Duration.ofMillis(500))
        .until(() -> setupHelper.validateSchemaCreation(testPrefix));
  }

  private ElasticsearchContainer setupElasticsearch() {
    final ElasticsearchContainer elasticsearchContainer =
        TestSearchContainers.createDefeaultElasticsearchContainer()
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

  private List<User> findUsers(
      final Class<?> testClass, final Object testInstance, Predicate<Field> predicate) {
    final var users = new ArrayList<User>();
    predicate =
        predicate.and(
            field ->
                field.getType() == User.class && field.getAnnotation(UserDefinition.class) != null);
    for (final Field field : testClass.getDeclaredFields()) {
      try {
        if (predicate.test(field)) {
          field.setAccessible(true);
          final var user = (User) field.get(testInstance);
          users.add(user);
        }
      } catch (final Exception ex) {
        throw new RuntimeException(ex);
      }
    }
    return users;
  }

  private void injectStaticClientField(final Class<?> testClass) {
    for (final Field field : testClass.getDeclaredFields()) {
      try {
        if (field.getType() == CamundaClient.class) {
          if (ModifierSupport.isStatic(field)) {
            field.setAccessible(true);
            field.set(null, createCamundaClient(field.getAnnotation(Authenticated.class)));
          } else {
            fail("Camunda Client field couldn't be injected. Make sure it is static.");
          }
        }
      } catch (final Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  @Override
  public void afterAll(final ExtensionContext context) {
    CloseHelper.quietCloseAll(closeables);
  }

  @Override
  public boolean supportsParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return parameterContext.getParameter().getType() == CamundaClient.class;
  }

  @Override
  public Object resolveParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return createCamundaClient(parameterContext.getParameter().getAnnotation(Authenticated.class));
  }

  private CamundaClient createCamundaClient(final Authenticated authenticated) {
    final CamundaClient camundaClient =
        authenticatedClientFactory.createCamundaClient(
            applicationUnderTest.application, authenticated);
    closeables.add(camundaClient);
    return camundaClient;
  }

  record ApplicationUnderTest(TestStandaloneApplication<?> application, boolean shouldBeManaged) {}

  private static final class NoopDBSetupHelper implements MultiDbSetupHelper {
    @Override
    public boolean validateConnection() {
      return true;
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

  public enum DatabaseType {
    LOCAL,
    ES,
    OS,
    RDBMS,
    AWS_OS
  }
}
