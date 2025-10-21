/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.utils.SchemaManagerITInvocationProvider.CONFIG_PREFIX;
import static io.camunda.search.schema.utils.SchemaTestUtil.createSchemaManager;
import static io.camunda.search.schema.utils.SchemaTestUtil.createTestIndexDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.createTestTemplateDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.mappingsMatch;
import static io.camunda.search.schema.utils.SchemaTestUtil.searchEngineClientFromConfig;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.utils.SchemaManagerITInvocationProvider;
import io.camunda.search.schema.utils.TestIndexDescriptor;
import io.camunda.search.schema.utils.TestTemplateDescriptor;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.index.MetadataIndex;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisabledIfSystemProperty(
    named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
    matches = "^(?=\\s*\\S).*$",
    disabledReason = "Excluding from AWS OS IT CI")
@ExtendWith(SchemaManagerITInvocationProvider.class)
public class SchemaManagerConcurrencyIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaManagerConcurrencyIT.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private TestIndexDescriptor index;
  private TestTemplateDescriptor indexTemplate;
  private MetadataIndex metadataIndex;
  private final MethodInterceptor methodInterceptor = new MethodInterceptor(SchemaManager.class);

  @BeforeAll
  public static void beforeAll() {
    ByteBuddyAgent.install();
  }

  @AfterEach
  public void reset() {
    methodInterceptor.reset();
  }

  @BeforeEach
  public void before() throws IOException {
    indexTemplate = createTestTemplateDescriptor("template_name", "/mappings.json");
    index = createTestIndexDescriptor("index_name", "/mappings.json");
    metadataIndex = new MetadataIndex(CONFIG_PREFIX, true);
  }

  @TestTemplate
  void shouldConcurrentlyCreateSchemaWithSuccess(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given
    final var exceptions = Collections.synchronizedList(new ArrayList<Throwable>());
    final var schemaManager1 =
        createSchemaManager(
            Set.of(index, metadataIndex), Set.of(indexTemplate), config, VersionUtil.getVersion());
    final var schemaManager2 =
        createSchemaManager(
            Set.of(index, metadataIndex), Set.of(indexTemplate), config, VersionUtil.getVersion());

    // when
    methodInterceptor.applyPostMethodAdvice("getMissingIndices");
    // Start schemaManager1 in a separate thread (it will pause after `getMissingIndices()`)
    final Thread thread1 =
        new Thread(
            collectExceptions(
                exceptions,
                () -> {
                  PostMethodPauseAdvice.setPauseForCurrentThread();
                  schemaManager1.startup();
                }),
            "schema-manager-1");
    thread1.start();
    // Start schemaManager2 that should execute while schemaManager1 is paused
    final Thread thread2 =
        new Thread(
            collectExceptions(exceptions, () -> schemaManager2.startup()), "schema-manager-2");
    thread2.start();
    thread2.join(TIMEOUT);
    // unpause schemaManager1
    PostMethodPauseAdvice.unpauseAll();
    thread1.join(TIMEOUT);

    // then
    assertThat(exceptions).isEmpty();
    // assert that both schema managers detected missing indices ("index_name", "template_name" and
    // "schema_metadata")
    assertThat(PostMethodPauseAdvice.getReturnedValueBeforePause(thread1, List.class)).hasSize(3);
    assertThat(PostMethodPauseAdvice.getReturnedValueBeforePause(thread2, List.class)).hasSize(3);
    // assert that the schema was correctly created
    final var retrievedIndex = clientAdapter.getIndexAsNode(index.getFullQualifiedName());
    final var retrievedIndexTemplate =
        clientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings.json")).isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"), "/mappings.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldConcurrentlyUpdateSchemaWithSuccess(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given
    final var exceptions = Collections.synchronizedList(new ArrayList<Throwable>());
    final var schemaManager1 =
        createSchemaManager(
            Set.of(index, metadataIndex), Set.of(indexTemplate), config, VersionUtil.getVersion());
    final var schemaManager2 =
        createSchemaManager(
            Set.of(index, metadataIndex), Set.of(indexTemplate), config, VersionUtil.getVersion());
    schemaManager1.startup(); // initial schema creation
    // set the mappings to a different file
    index.setMappingsClasspathFilename("/mappings-added-property.json");
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");

    // when
    methodInterceptor.applyPostMethodAdvice("validateIndices");
    // Start schemaManager1 in a separate thread (it will pause after `validateIndices()`)
    final Thread thread1 =
        new Thread(
            collectExceptions(
                exceptions,
                () -> {
                  PostMethodPauseAdvice.setPauseForCurrentThread();
                  schemaManager1.startup();
                }),
            "schema-manager-1");
    thread1.start();
    // Start schemaManager2 that should execute while schemaManager1 is paused
    final Thread thread2 =
        new Thread(
            collectExceptions(exceptions, () -> schemaManager2.startup()), "schema-manager-2");
    thread2.start();
    thread2.join(TIMEOUT);
    // unpause schemaManager1
    PostMethodPauseAdvice.unpauseAll();
    thread1.join(TIMEOUT);

    // then
    assertThat(exceptions).isEmpty();
    // assert that both schema managers detected diffs in the index and index template mappings
    assertThat(PostMethodPauseAdvice.getReturnedValueBeforePause(thread1, Map.class)).hasSize(2);
    assertThat(PostMethodPauseAdvice.getReturnedValueBeforePause(thread2, Map.class)).hasSize(2);
    // assert that the schema was correctly updated
    final var retrievedIndex = clientAdapter.getIndexAsNode(index.getFullQualifiedName());
    final var retrievedIndexTemplate =
        clientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());
    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldNotErrorIfOldSchemaManagerStartsWhileNewSchemaManagerHasAlreadyStarted(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given
    final var updatedSchemaManager =
        createSchemaManager(
            Set.of(index, metadataIndex), Set.of(indexTemplate), config, VersionUtil.getVersion());
    final var oldSchemaManager =
        createSchemaManager(
            Set.of(index, metadataIndex), Set.of(indexTemplate), config, VersionUtil.getVersion());
    oldSchemaManager.startup(); // initial schema creation

    // when
    index.setMappingsClasspathFilename("/mappings-added-property.json");
    indexTemplate.setMappingsClasspathFilename("/mappings-added-property.json");

    updatedSchemaManager.startup();

    index.setMappingsClasspathFilename("/mappings.json");
    indexTemplate.setMappingsClasspathFilename("/mappings.json");

    oldSchemaManager.startup();

    // then
    final var retrievedIndex = clientAdapter.getIndexAsNode(index.getFullQualifiedName());
    final var retrievedIndexTemplate =
        clientAdapter.getIndexTemplateAsNode(indexTemplate.getTemplateName());

    assertThat(mappingsMatch(retrievedIndex.get("mappings"), "/mappings-added-property.json"))
        .isTrue();
    assertThat(
            mappingsMatch(
                retrievedIndexTemplate.at("/index_template/template/mappings"),
                "/mappings-added-property.json"))
        .isTrue();
  }

  @TestTemplate
  void shouldHandleSequentialNodeUpgradeSuccessfully(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // Scenario: Sequential node upgrade
    // Initial State: Node1(1.0.0), Node2(1.0.0), Schema(1.0.0)
    // Step 1: Node1 upgrades to 1.1.0
    // Step 2: Node2 upgrades to 1.1.0
    // Expected: Schema version should be 1.1.0, no errors

    // given - Initial State: Schema(1.0.0)
    final var node1Version10 =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.0.0");
    node1Version10.startup(); // Initial schema creation with 1.0.0
    assertThat(getSchemaVersion(config)).isEqualTo("1.0.0");

    // when - Step 1: Node1 upgrades to 1.1.0
    final var node1Version11 =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.1.0");
    node1Version11.startup();

    // then - Schema should be upgraded to 1.1.0
    assertThat(getSchemaVersion(config)).isEqualTo("1.1.0");

    // when - Step 2: Node2 upgrades to 1.1.0 (should skip upgrade since schema is already 1.1.0)
    final var node2Version11 =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.1.0");
    node2Version11.startup();

    // then - Schema should still be 1.1.0
    assertThat(getSchemaVersion(config)).isEqualTo("1.1.0");
  }

  @TestTemplate
  void shouldHandleParallelNodeUpgradeSuccessfully(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) throws Exception {
    // Scenario: Parallel Node Upgrade
    // Initial State: Node1(1.0.0), Node2(1.0.0), Schema(1.0.0)
    // Step 1: Both nodes upgrade simultaneously to 1.1.0
    // Expected: Schema version should be 1.1.0, both upgrades should succeed idempotently

    // given - Initial State: Schema(1.0.0)
    final var initialNode =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.0.0");
    initialNode.startup();
    assertThat(getSchemaVersion(config)).isEqualTo("1.0.0");

    final var exceptions = Collections.synchronizedList(new ArrayList<Throwable>());

    // when - Both nodes upgrade simultaneously
    final var node1Version11 =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.1.0");
    final var node2Version11 =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.1.0");

    // when - Step 1: Node1 starts upgrade but pauses after checkVersionCompatibility
    methodInterceptor.applyPostMethodAdvice("checkVersionCompatibility");
    final Thread thread1 =
        new Thread(
            collectExceptions(
                exceptions,
                () -> {
                  PostMethodPauseAdvice.setPauseForCurrentThread();
                  node1Version11.startup();
                }),
            "node1-upgrade");
    thread1.start();

    // Step 2: Node2 starts upgrade (should upgrade while Node1 is paused)
    final Thread thread2 =
        new Thread(collectExceptions(exceptions, node2Version11::startup), "node2-restart");
    thread2.start();
    thread2.join(TIMEOUT);

    // then - Schema should be 1.1.0 at this point
    assertThat(getSchemaVersion(config)).isEqualTo("1.1.0");

    // when - Step 3: Node1 completes upgrade
    PostMethodPauseAdvice.unpauseAll();
    thread1.join(TIMEOUT);

    // then - Both should complete without errors
    assertThat(exceptions).isEmpty();
    // Schema should be upgraded to 1.1.0
    assertThat(getSchemaVersion(config)).isEqualTo("1.1.0");
    // then - verify the version compatibility results observed by both nodes
    assertThat(PostMethodPauseAdvice.getReturnedValueBeforePause(thread1, CheckResult.class))
        .isInstanceOf(Compatible.MinorUpgrade.class)
        .extracting("from", "to")
        .map(v -> v.toString())
        .containsExactly("1.0.0", "1.1.0");
    assertThat(PostMethodPauseAdvice.getReturnedValueBeforePause(thread2, CheckResult.class))
        .isInstanceOf(Compatible.MinorUpgrade.class)
        .extracting("from", "to")
        .map(v -> v.toString())
        .containsExactly("1.0.0", "1.1.0");
  }

  @TestTemplate
  void shouldHandleOldVersionNodeRestartAfterUpgrade(
      final SearchEngineConfiguration config, final SearchClientAdapter ignored) {
    // Scenario: Old version node restarts after upgrade
    // Initial State: Node1(1.0.0), Node2(1.0.0), Schema(1.0.0)
    // Step 1: Node1 upgrades to 1.1.0
    // Step 2: Node2 restarts (still 1.0.0) - should skip (version downgrade)
    // Expected: Schema remains 1.1.0, no errors

    // given - Initial State: Schema(1.0.0)
    final var node1Version10 =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.0.0");
    node1Version10.startup();
    assertThat(getSchemaVersion(config)).isEqualTo("1.0.0");

    // when - Step 1: Node1 upgrades to 1.1.0
    final var node1Version11 =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.1.0");
    node1Version11.startup();
    assertThat(getSchemaVersion(config)).isEqualTo("1.1.0");

    // when - Step 2: Node2 restarts with old version 1.0.0 (should skip - minor downgrade)
    final var node2Version10 =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.0.0");
    node2Version10.startup();

    // then - Schema should remain 1.1.0 (not downgraded)
    assertThat(getSchemaVersion(config)).isEqualTo("1.1.0");
  }

  @TestTemplate
  void shouldHandleOldVersionNodeRestartDuringUpgrade(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // Scenario: Old version node restart during upgrade
    // Initial State: Node1(1.0.0), Node2(1.0.0), Schema(1.0.0)
    // Step 1: Node1 starts upgrade to 1.1.0 (but pauses before storing version)
    // Step 2: Node2 restarts (still 1.0.0) - should skip (same version)
    // Step 3: Node1 completes upgrade to 1.1.0
    // Expected: Schema version should be 1.1.0, no errors

    // given - Initial State: Schema(1.0.0)
    final var initialNode =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.0.0");
    initialNode.startup();
    assertThat(getSchemaVersion(config)).isEqualTo("1.0.0");

    final var exceptions = Collections.synchronizedList(new ArrayList<Throwable>());

    // when - Step 1: Node1 starts upgrade but pauses after checkVersionCompatibility
    methodInterceptor.applyPostMethodAdvice("checkVersionCompatibility");
    final var node1Version11 =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.1.0");

    final Thread thread1 =
        new Thread(
            collectExceptions(
                exceptions,
                () -> {
                  PostMethodPauseAdvice.setPauseForCurrentThread();
                  node1Version11.startup();
                }),
            "node1-upgrade");
    thread1.start();

    // Step 2: Node2 restarts with old version 1.0.0 (should skip - same version as schema)
    final var node2Version10 =
        createSchemaManager(Set.of(index, metadataIndex), Set.of(indexTemplate), config, "1.0.0");
    final Thread thread2 =
        new Thread(collectExceptions(exceptions, node2Version10::startup), "node2-restart");
    thread2.start();
    thread2.join(TIMEOUT);

    // then - Schema should still be 1.0.0 at this point
    assertThat(getSchemaVersion(config)).isEqualTo("1.0.0");

    // when - Step 3: Node1 completes upgrade
    PostMethodPauseAdvice.unpauseAll();
    thread1.join(TIMEOUT);

    // then - Schema should now be 1.1.0
    assertThat(exceptions).isEmpty();
    assertThat(getSchemaVersion(config)).isEqualTo("1.1.0");
    // then - verify the version compatibility results observed by both nodes
    assertThat(PostMethodPauseAdvice.getReturnedValueBeforePause(thread1, CheckResult.class))
        .isInstanceOf(Compatible.MinorUpgrade.class)
        .extracting("from", "to")
        .map(v -> v.toString())
        .containsExactly("1.0.0", "1.1.0");
    assertThat(PostMethodPauseAdvice.getReturnedValueBeforePause(thread2, CheckResult.class))
        .isInstanceOf(Compatible.SameVersion.class)
        .extracting("version")
        .extracting(v -> v.toString())
        .isEqualTo("1.0.0");
  }

  private Runnable collectExceptions(final List<Throwable> exceptions, final Runnable runnable) {
    return () -> {
      try {
        runnable.run();
      } catch (final Throwable t) {
        LOGGER.error("Exception in thread {}", Thread.currentThread().getName(), t);
        exceptions.add(t);
      }
    };
  }

  private String getSchemaVersion(final SearchEngineConfiguration config) {
    final var searchEngineClient = searchEngineClientFromConfig(config);
    final var schemaVersionDoc =
        searchEngineClient.getDocument(
            metadataIndex.getFullQualifiedName(), SchemaMetadataStore.SCHEMA_VERSION_METADATA_ID);
    if (schemaVersionDoc != null) {
      return (String) schemaVersionDoc.get(MetadataIndex.VALUE);
    }
    return null;
  }

  private static SchemaManager createSchemaManager(
      final Collection<IndexDescriptor> indexDescriptors,
      final Collection<IndexTemplateDescriptor> templateDescriptors,
      final SearchEngineConfiguration config,
      final String version) {
    return new SchemaManager(
        searchEngineClientFromConfig(config),
        indexDescriptors,
        templateDescriptors,
        config,
        new IndexSchemaValidator(TestObjectMapper.objectMapper()),
        version,
        null);
  }

  static class PostMethodPauseAdvice {
    static final Semaphore SEMAPHORE = new Semaphore(0);
    static ThreadLocal<Boolean> pauseCurrentThread = ThreadLocal.withInitial(() -> false);
    static final ConcurrentMap<Thread, Object> RETURNED_VALUES = new ConcurrentHashMap<>();

    @Advice.OnMethodExit
    public static void onExit(@Advice.Return final Object returnedObject)
        throws InterruptedException {
      RETURNED_VALUES.put(Thread.currentThread(), returnedObject);
      if (pauseCurrentThread.get()) {
        SEMAPHORE.acquire(); // wait for unpauseAll signal
      }
      pauseCurrentThread.set(false);
    }

    static void setPauseForCurrentThread() {
      pauseCurrentThread.set(true);
    }

    static void unpauseAll() {
      while (SEMAPHORE.hasQueuedThreads()) {
        SEMAPHORE.release();
      }
    }

    static <T> T getReturnedValueBeforePause(final Thread thread, final Class<T> clazz) {
      return clazz.cast(RETURNED_VALUES.get(thread));
    }

    static void reset() {
      unpauseAll();
      RETURNED_VALUES.clear();
      pauseCurrentThread = ThreadLocal.withInitial(() -> false);
    }
  }

  static class MethodInterceptor {

    private final Class<?> targetClass;

    public MethodInterceptor(final Class<?> targetClass) {
      this.targetClass = targetClass;
    }

    public void applyPostMethodAdvice(final String pauseMethodName) {
      new ByteBuddy()
          .redefine(targetClass)
          .visit(Advice.to(PostMethodPauseAdvice.class).on(named(pauseMethodName)))
          .make()
          .load(targetClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
    }

    public void reset() {
      new ByteBuddy()
          .redefine(targetClass)
          .make()
          .load(targetClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
      PostMethodPauseAdvice.reset();
    }
  }
}
