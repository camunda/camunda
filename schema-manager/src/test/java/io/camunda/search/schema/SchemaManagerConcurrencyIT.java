/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

import static io.camunda.search.schema.utils.SchemaTestUtil.createSchemaManager;
import static io.camunda.search.schema.utils.SchemaTestUtil.createTestIndexDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.createTestTemplateDescriptor;
import static io.camunda.search.schema.utils.SchemaTestUtil.mappingsMatch;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.utils.SchemaManagerITInvocationProvider;
import io.camunda.search.schema.utils.TestIndexDescriptor;
import io.camunda.search.schema.utils.TestTemplateDescriptor;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.search.test.utils.SearchDBExtension;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
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
  }

  @TestTemplate
  void shouldConcurrentlyCreateSchemaWithSuccess(
      final SearchEngineConfiguration config, final SearchClientAdapter clientAdapter)
      throws Exception {
    // given
    final var exceptions = Collections.synchronizedList(new ArrayList<Throwable>());
    final var schemaManager1 = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);
    final var schemaManager2 = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);

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
    // assert that both schema managers detected missing indices ("index_name" and "template_name")
    assertThat(PostMethodPauseAdvice.getReturnedValueBeforePause(thread1, List.class)).hasSize(2);
    assertThat(PostMethodPauseAdvice.getReturnedValueBeforePause(thread2, List.class)).hasSize(2);
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
    final var schemaManager1 = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);
    final var schemaManager2 = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);
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
        createSchemaManager(Set.of(index), Set.of(indexTemplate), config);
    final var oldSchemaManager = createSchemaManager(Set.of(index), Set.of(indexTemplate), config);
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
