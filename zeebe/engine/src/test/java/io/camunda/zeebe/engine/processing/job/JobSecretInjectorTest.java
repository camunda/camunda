/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.secretstore.InMemorySecretCache;
import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretCache;
import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.job.JobSecretInjector.DroppedJob;
import io.camunda.zeebe.engine.processing.job.JobSecretInjector.FailedInjectionJob;
import io.camunda.zeebe.engine.processing.job.JobSecretInjector.OversizedJob;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class JobSecretInjectorTest {

  private static final String STORE_ID = SecretStoreRegistry.DEFAULT_STORE_ID;

  private static JobSecretInjector injector(final Map<String, String> cachedSecrets) {
    final var cache = new InMemorySecretCache();
    cachedSecrets.forEach(cache::put);
    return new JobSecretInjector(registryWith(cache));
  }

  private static SecretStoreRegistry registryWith(final SecretCache cache) {
    return new SecretStoreRegistry(
        Map.of("default", new NoopSecretStore()), Map.of("default", cache));
  }

  private static JobBatchRecord batchWith(final JobRecord... jobs) {
    final var batch = new JobBatchRecord().setType("task-type");
    long key = 100;
    for (final JobRecord job : jobs) {
      batch.jobKeys().add().setValue(key++);
      batch.jobs().add().copyFrom(job);
    }
    return batch;
  }

  /**
   * Mirrors the collector: checks each job, appends only the activatable ones to the batch (keys
   * starting at 100, in the given order), registers the appended jobs with secret references for
   * the injection, and registers the skipped jobs with a non-cached reference for the resolution.
   */
  private static JobBatchRecord collect(final JobSecretInjector injector, final JobRecord... jobs) {
    final var batch = new JobBatchRecord().setType("task-type");
    injector.reset();
    long key = 100;
    for (final JobRecord job : jobs) {
      final var check = injector.checkSecrets(job);
      if (check.nonCachedSecrets().isEmpty()) {
        batch.jobKeys().add().setValue(key);
        final JobRecord appendedJob = batch.jobs().add();
        appendedJob.copyFrom(job);
        injector.registerForInjection(check, batch.jobs().size() - 1, appendedJob);
      } else {
        injector.registerForResolution(check, key);
      }
      key++;
    }
    return batch;
  }

  private static Map<String, List<Long>> resolutionsByReferenceName(
      final JobSecretInjector injector) {
    return byReferenceName(injector.jobsWithNonCachedSecrets());
  }

  private static Map<String, List<Long>> byReferenceName(
      final Map<SecretReference, List<Long>> jobsByReference) {
    final Map<String, List<Long>> byName = new LinkedHashMap<>();
    jobsByReference.forEach((reference, keys) -> byName.put(reference.name(), keys));
    return byName;
  }

  private static JobBatchRecord copyOf(final JobBatchRecord batch) {
    final var copy = new JobBatchRecord();
    copy.copyFrom(batch);
    return copy;
  }

  private static JobRecord job(final Map<String, ?> variables, final SecretRef... refs) {
    final var job =
        new JobRecord()
            .setVariables(BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(variables)));
    for (final SecretRef ref : refs) {
      job.addSecretReference(ref.storeId(), ref.name(), ref.path());
    }
    return job;
  }

  /**
   * A job whose variables buffer is a truncated msgpack document: a one-entry map header ({@code
   * 0x81}) without the entry, which passes the root-level object check of {@code setVariables} but
   * fails to parse.
   */
  private static JobRecord brokenVariablesJob(final SecretRef... refs) {
    final var job = new JobRecord().setVariables(BufferUtil.wrapArray(new byte[] {(byte) 0x81}));
    for (final SecretRef ref : refs) {
      job.addSecretReference(ref.storeId(), ref.name(), ref.path());
    }
    return job;
  }

  private static SecretRef ref(final String name, final String path) {
    return new SecretRef(STORE_ID, name, path);
  }

  private static Map<String, Object> variablesOf(final JobBatchRecord batch, final int index) {
    final var variables = variablesOfAllJobs(batch);
    if (index >= variables.size()) {
      throw new IllegalArgumentException("no job at index " + index);
    }
    return variables.get(index);
  }

  private static List<Map<String, Object>> variablesOfAllJobs(final JobBatchRecord batch) {
    final List<Map<String, Object>> variables = new ArrayList<>();
    for (final JobRecord job : batch.jobs()) {
      variables.add(job.getVariables());
    }
    return variables;
  }

  private static List<Long> jobKeysOf(final JobBatchRecord batch) {
    final List<Long> keys = new ArrayList<>();
    for (final LongValue key : batch.jobKeys()) {
      keys.add(key.getValue());
    }
    return keys;
  }

  @Nested
  final class CheckSecrets {

    @Test
    void shouldSkipJobWhoseSecretIsNotCached() {
      // given - a cached job, an uncached job, and a job without references
      final var injector = injector(Map.of("token", "resolved"));

      // when
      final var batch =
          collect(
              injector,
              job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")),
              job(Map.of("auth", "camunda.secrets.other"), ref("other", "/auth")),
              job(Map.of("foo", "bar")));

      // then - the uncached job is not collected, the others keep their keys aligned
      assertThat(variablesOfAllJobs(batch))
          .containsExactly(Map.of("auth", "camunda.secrets.token"), Map.of("foo", "bar"));
      assertThat(jobKeysOf(batch)).containsExactly(100L, 102L);

      // and - the collected secret job gets its cached value injected
      final var response = copyOf(batch);
      injector.injectSecretValues(response, batch, batch.getLength(), length -> true);
      assertThat(variablesOf(response, 0)).isEqualTo(Map.of("auth", "resolved"));
    }

    @Test
    void shouldCollectAllJobsWhenAllSecretsAreCached() {
      // given
      final var injector = injector(Map.of("token", "t", "apiKey", "k"));

      // when
      final var batch =
          collect(
              injector,
              job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")),
              job(Map.of("key", "camunda.secrets.apiKey"), ref("apiKey", "/key")));

      // then - both jobs are collected and get their cached values injected
      assertThat(jobKeysOf(batch)).containsExactly(100L, 101L);
      final var response = copyOf(batch);
      injector.injectSecretValues(response, batch, batch.getLength(), length -> true);
      assertThat(variablesOfAllJobs(response))
          .containsExactly(Map.of("auth", "t"), Map.of("key", "k"));
    }

    @Test
    void shouldSkipJobWhenOnlySomeOfItsSecretsAreCached() {
      // given - one of the job's two references has no cached value
      final var injector = injector(Map.of("token", "t"));

      // when
      final var batch =
          collect(
              injector,
              job(
                  Map.of("auth", "camunda.secrets.token", "key", "camunda.secrets.apiKey"),
                  ref("token", "/auth"),
                  ref("apiKey", "/key")));

      // then
      assertThat(jobKeysOf(batch)).isEmpty();
      assertThat(variablesOfAllJobs(batch)).isEmpty();
      assertThat(injector.hasSecretsToInject()).isFalse();
    }

    @Test
    void shouldReportAllNonCachedSecretsOfAJob() {
      // given - one cached and two non-cached references on the same job
      final var injector = injector(Map.of("token", "t"));
      final var job =
          job(
              Map.of(
                  "auth", "camunda.secrets.token",
                  "key", "camunda.secrets.apiKey",
                  "other", "camunda.secrets.other"),
              ref("token", "/auth"),
              ref("apiKey", "/key"),
              ref("other", "/other"));

      // when
      final var check = injector.checkSecrets(job);

      // then - every non-cached reference is reported, not only the first miss
      assertThat(check.cachedSecrets())
          .extracting(cachedSecret -> cachedSecret.secret().reference().name())
          .containsExactly("token");
      assertThat(check.nonCachedSecrets())
          .extracting(secret -> secret.reference().name())
          .containsExactlyInAnyOrder("apiKey", "other");
    }

    @Test
    void shouldSkipNonAdjacentJobsAndKeepKeysAligned() {
      // given - uncached jobs at position 0 and 2
      final var injector = injector(Map.of("token", "t"));

      // when
      final var batch =
          collect(
              injector,
              job(Map.of("a", "camunda.secrets.other"), ref("other", "/a")),
              job(Map.of("b", "camunda.secrets.token"), ref("token", "/b")),
              job(Map.of("c", "camunda.secrets.another"), ref("another", "/c")),
              job(Map.of("d", "plain")));

      // then
      assertThat(variablesOfAllJobs(batch))
          .containsExactly(Map.of("b", "camunda.secrets.token"), Map.of("d", "plain"));
      assertThat(jobKeysOf(batch)).containsExactly(101L, 103L);
    }

    @Test
    void shouldPropagateCacheLookupFailure() {
      // given - a broken cache; the failure must reach the processor instead of being swallowed
      final SecretCache throwingCache =
          new SecretCache() {
            @Override
            public Optional<String> get(final String name) {
              throw new IllegalStateException("cache is broken");
            }

            @Override
            public void put(final String name, final String value) {}

            @Override
            public void remove(final String name) {}
          };
      final var injector = new JobSecretInjector(registryWith(throwingCache));
      final var job = job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth"));

      // when/then
      assertThatThrownBy(() -> injector.checkSecrets(job))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("cache is broken");
    }

    @Test
    void shouldSkipSecretJobsWhenNoStoreIsConfigured() {
      // given - an empty registry has no caches, so no reference resolves
      final var injector = new JobSecretInjector(new SecretStoreRegistry(Map.of()));

      // when
      final var batch =
          collect(
              injector,
              job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")),
              job(Map.of("foo", "bar")));

      // then
      assertThat(variablesOfAllJobs(batch)).containsExactly(Map.of("foo", "bar"));
    }

    @Test
    void shouldResolveReferenceFromTheStoreItNames() {
      // given - two stores; the reference names the second store explicitly
      final var cache = new InMemorySecretCache();
      cache.put("token", "resolved");
      final var registry =
          new SecretStoreRegistry(
              Map.of("store-a", new NoopSecretStore(), "store-b", new NoopSecretStore()),
              Map.of("store-a", new InMemorySecretCache(), "store-b", cache));
      final var injector = new JobSecretInjector(registry);

      // when
      final var batch =
          collect(
              injector,
              job(
                  Map.of("auth", "camunda.secrets.token"),
                  new SecretRef("store-b", "token", "/auth")));

      // then - the job is collected and the value of the named store is injected
      assertThat(jobKeysOf(batch)).containsExactly(100L);
      final var response = copyOf(batch);
      injector.injectSecretValues(response, batch, batch.getLength(), length -> true);
      assertThat(variablesOf(response, 0)).isEqualTo(Map.of("auth", "resolved"));
    }

    @Test
    void shouldNotResolveReferenceNamingAnUnconfiguredStore() {
      // given - a store holding the secret, and a reference naming a store next to it that is not
      // configured
      final var cache = new InMemorySecretCache();
      cache.put("token", "resolved");
      final var registry =
          new SecretStoreRegistry(
              Map.of("store-a", new NoopSecretStore()), Map.of("store-a", cache));
      final var injector = new JobSecretInjector(registry);

      // when
      final var batch =
          collect(
              injector,
              job(
                  Map.of("auth", "camunda.secrets.token"),
                  new SecretRef("store-b", "token", "/auth")));

      // then - the job is skipped rather than served from the store that happens to hold the name:
      // the store lookup is exact
      assertThat(jobKeysOf(batch)).isEmpty();
    }

    @Test
    void shouldNotResolveDefaultReferenceFromAStoreUnderAnotherId() {
      // given - a store holding the secret under an id other than the default one
      final var cache = new InMemorySecretCache();
      cache.put("token", "resolved");
      final var registry =
          new SecretStoreRegistry(
              Map.of("store-a", new NoopSecretStore()), Map.of("store-a", cache));
      final var injector = new JobSecretInjector(registry);

      // when - the reference addresses the default store, as camunda.secrets.<name> always does
      final var batch =
          collect(injector, job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")));

      // then - the job is skipped instead of falling back to the sole configured store
      assertThat(jobKeysOf(batch)).isEmpty();
    }

    @Test
    void shouldNotRegisterJobsWithoutSecretReferences() {
      // given
      final var injector = injector(Map.of());

      // when
      final var batch = collect(injector, job(Map.of("foo", "bar")), job(Map.of("baz", "qux")));

      // then
      assertThat(jobKeysOf(batch)).containsExactly(100L, 101L);
      assertThat(injector.hasSecretsToInject()).isFalse();
    }
  }

  @Nested
  final class RegisterForResolution {

    @Test
    void shouldGroupWaitingJobsByNonCachedReference() {
      // given - two jobs waiting on the same uncached reference, a third on another
      final var injector = injector(Map.of());

      // when
      collect(
          injector,
          job(Map.of("a", "camunda.secrets.token"), ref("token", "/a")),
          job(Map.of("b", "camunda.secrets.token"), ref("token", "/b")),
          job(Map.of("c", "camunda.secrets.other"), ref("other", "/c")));

      // then - one entry per reference, with the keys of the jobs waiting on it in order
      assertThat(resolutionsByReferenceName(injector))
          .containsOnly(entry("token", List.of(100L, 101L)), entry("other", List.of(102L)));
    }

    @Test
    void shouldRecordJobOncePerReferenceWhenReferenceRepeats() {
      // given - one job referencing the same uncached secret at two paths
      final var injector = injector(Map.of());

      // when
      collect(
          injector,
          job(
              Map.of("a", "camunda.secrets.token", "b", "camunda.secrets.token"),
              ref("token", "/a"),
              ref("token", "/b")));

      // then - the job is recorded once for the reference
      assertThat(resolutionsByReferenceName(injector)).containsOnly(entry("token", List.of(100L)));
    }

    @Test
    void shouldRegisterOnlyNonCachedReferencesOfASkippedJob() {
      // given - a job with one cached and one non-cached reference
      final var injector = injector(Map.of("token", "resolved"));

      // when
      collect(
          injector,
          job(
              Map.of("a", "camunda.secrets.token", "b", "camunda.secrets.other"),
              ref("token", "/a"),
              ref("other", "/b")));

      // then - only the non-cached reference is registered for resolution
      assertThat(resolutionsByReferenceName(injector)).containsOnly(entry("other", List.of(100L)));
    }

    @Test
    void shouldRegisterNothingWhenAllJobsAreActivatable() {
      // given - a fully cached secret job and a job without references
      final var injector = injector(Map.of("token", "resolved"));

      // when
      collect(
          injector,
          job(Map.of("a", "camunda.secrets.token"), ref("token", "/a")),
          job(Map.of("foo", "bar")));

      // then
      assertThat(injector.jobsWithNonCachedSecrets()).isEmpty();
    }

    @Test
    void shouldClearRegisteredResolutionsOnReset() {
      // given
      final var injector = injector(Map.of());
      collect(injector, job(Map.of("a", "camunda.secrets.token"), ref("token", "/a")));
      assertThat(injector.jobsWithNonCachedSecrets()).isNotEmpty();

      // when
      injector.reset();

      // then
      assertThat(injector.jobsWithNonCachedSecrets()).isEmpty();
    }

    @Test
    void shouldReturnRegisteredResolutionsAsImmutableSnapshot() {
      // given
      final var injector = injector(Map.of());
      collect(
          injector,
          job(Map.of("a", "camunda.secrets.token"), ref("token", "/a")),
          job(Map.of("b", "camunda.secrets.token"), ref("token", "/b")));
      final var snapshot = injector.jobsWithNonCachedSecrets();

      // when - the caller tries to change the snapshot, and the next command resets the injector
      assertThatThrownBy(() -> snapshot.values().iterator().next().add(999L))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
      injector.reset();

      // then - the snapshot still holds the job keys registered when it was taken
      assertThat(byReferenceName(snapshot)).containsOnly(entry("token", List.of(100L, 101L)));
    }
  }

  @Nested
  final class InjectSecretValues {

    @Test
    void shouldInjectCachedSecretAtPath() {
      // given
      final var batch =
          batchWith(
              job(
                  Map.of("tokens", Map.of("externalSystemToken", "camunda.secrets.token")),
                  ref("token", "/tokens/externalSystemToken")));

      // when
      inject(batch, Map.of("token", "resolved-token"));

      // then
      assertThat(variablesOf(batch, 0))
          .isEqualTo(Map.of("tokens", Map.of("externalSystemToken", "resolved-token")));
    }

    @Test
    void shouldReplaceReferenceEmbeddedInString() {
      // given
      final var batch =
          batchWith(
              job(
                  Map.of("authorization", "Bearer camunda.secrets.token"),
                  ref("token", "/authorization")));

      // when
      inject(batch, Map.of("token", "xyz"));

      // then
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("authorization", "Bearer xyz"));
    }

    @Test
    void shouldInjectMultipleReferencesAtSamePath() {
      // given
      final var batch =
          batchWith(
              job(
                  Map.of("h", "camunda.secrets.token camunda.secrets.postfix"),
                  ref("token", "/h"),
                  ref("postfix", "/h")));

      // when
      inject(batch, Map.of("token", "A", "postfix", "B"));

      // then
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("h", "A B"));
    }

    @Test
    void shouldInjectReferencesAtDifferentPaths() {
      // given
      final var batch =
          batchWith(
              job(
                  Map.of(
                      "auth",
                      Map.of("token", "camunda.secrets.token", "key", "camunda.secrets.apiKey")),
                  ref("token", "/auth/token"),
                  ref("apiKey", "/auth/key")));

      // when
      inject(batch, Map.of("token", "t", "apiKey", "k"));

      // then
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("auth", Map.of("token", "t", "key", "k")));
    }

    @Test
    void shouldNotCorruptPlaceholderThatIsPrefixOfAnother() {
      // given - "token" is a prefix of "token2", both injected into the same leaf
      final var batch =
          batchWith(
              job(
                  Map.of("h", "camunda.secrets.token camunda.secrets.token2"),
                  ref("token", "/h"),
                  ref("token2", "/h")));

      // when
      inject(batch, Map.of("token", "A", "token2", "BB"));

      // then - the longer placeholder is replaced first, so neither value is mangled
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("h", "A BB"));
    }

    @Test
    void shouldNotCorruptDashedPlaceholderThatIsPrefixOfAnother() {
      // given - the same collision with dashed names, which is the shape that actually occurs:
      // 'db-password' next to 'db-password-old' is an ordinary naming convention, 'token' next to
      // 'token2' is not
      final var batch =
          batchWith(
              job(
                  Map.of("h", "camunda.secrets.db-password camunda.secrets.db-password-old"),
                  ref("db-password", "/h"),
                  ref("db-password-old", "/h")));

      // when
      inject(batch, Map.of("db-password", "A", "db-password-old", "BB"));

      // then - neither value is mangled
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("h", "A BB"));
    }

    @Test
    void shouldInjectAtDeeplyNestedPathAndPreserveSiblings() {
      // given - siblings of every type around the addressed leaf
      final var variables =
          Map.of(
              "count",
              42,
              "ratio",
              1.5,
              "flag",
              true,
              "list",
              List.of(1, "two", Map.of("three", 3)),
              "outer",
              Map.of("inner", Map.of("leaf", "camunda.secrets.token", "keep", "as-is")));
      final var batch = batchWith(job(variables, ref("token", "/outer/inner/leaf")));

      // when
      inject(batch, Map.of("token", "secret-value"));

      // then - only the addressed leaf changed, everything else survives the injection untouched
      assertThat(variablesOf(batch, 0))
          .isEqualTo(
              Map.of(
                  "count",
                  42,
                  "ratio",
                  1.5,
                  "flag",
                  true,
                  "list",
                  List.of(1, "two", Map.of("three", 3)),
                  "outer",
                  Map.of("inner", Map.of("leaf", "secret-value", "keep", "as-is"))));
    }

    @Test
    void shouldUnescapePointerSegments() {
      // given - the variable name contains the characters escaped by RFC 6901
      final var batch =
          batchWith(job(Map.of("a/b~c", "camunda.secrets.token"), ref("token", "/a~1b~0c")));

      // when
      inject(batch, Map.of("token", "xyz"));

      // then
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("a/b~c", "xyz"));
    }

    @Test
    void shouldLeaveJobWithoutSecretReferencesUntouched() {
      // given
      final var original = Map.of("foo", "bar");
      final var batch = batchWith(job(original));

      // when
      inject(batch, Map.of("token", "t"));

      // then
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
    }

    @Test
    void shouldInjectOnlyRegisteredJobsInMultiJobBatch() {
      // given - a cached job, a job without references, and a job that fails the check (which the
      // collector would not have appended; if it still sits in a batch, it must stay untouched)
      final Map<String, Object> withSecret = Map.of("auth", "camunda.secrets.token");
      final Map<String, Object> withoutSecret = Map.of("foo", "bar");
      final Map<String, Object> uncached = Map.of("auth", "camunda.secrets.other");
      final var activated =
          batchWith(
              job(withSecret, ref("token", "/auth")),
              job(withoutSecret),
              job(uncached, ref("other", "/auth")));

      // when
      final var response = copyOf(activated);
      inject(response, activated, Map.of("token", "resolved"));

      // then - only the fully cached job gets its value injected on the response
      assertThat(variablesOfAllJobs(response))
          .containsExactly(Map.of("auth", "resolved"), withoutSecret, uncached);
      assertThat(variablesOfAllJobs(activated))
          .containsExactly(withSecret, withoutSecret, uncached);
    }

    @Test
    void shouldDropAndReportFirstJobWhoseValueGrowthCanNeverFit() {
      // given - injecting the cached value grows the first job beyond the whole batch budget, so
      // no batch could ever carry it
      final var oversized = "x".repeat(EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER + 100);
      final var original = Map.of("auth", "camunda.secrets.token");
      final var response = batchWith(job(original, ref("token", "/auth")));
      final var activated = copyOf(response);

      // when
      final var oversizedJob = inject(response, activated, Map.of("token", oversized));

      // then - the job is dropped from both batches and reported for an incident
      assertThat(variablesOfAllJobs(response)).isEmpty();
      assertThat(jobKeysOf(response)).isEmpty();
      assertThat(variablesOfAllJobs(activated)).isEmpty();
      assertThat(jobKeysOf(activated)).isEmpty();
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();
      assertThat(oversizedJob)
          .hasValueSatisfying(
              dropped -> {
                assertThat(dropped).isInstanceOf(OversizedJob.class);
                assertThat(dropped.jobKey()).isEqualTo(100L);
                assertThat(((OversizedJob) dropped).growth())
                    .isGreaterThan(EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER);
              });
    }

    @Test
    void shouldDropAndReportJobWhoseInjectionFails() {
      // given - the second job's variables are not valid msgpack, so its injection fails; a job
      // with a fitting value precedes it and a plain job follows it
      final Map<String, Object> fitting = Map.of("auth", "camunda.secrets.token");
      final var response =
          batchWith(
              job(fitting, ref("token", "/auth")),
              brokenVariablesJob(ref("token", "/auth")),
              job(Map.of("foo", "bar")));
      final var activated = copyOf(response);

      // when
      final var droppedJob = inject(response, activated, Map.of("token", "resolved"));

      // then - the failing job and every job after it are dropped from both batches; the job
      // before it keeps its injected value on the response only
      assertThat(variablesOfAllJobs(response)).containsExactly(Map.of("auth", "resolved"));
      assertThat(jobKeysOf(response)).containsExactly(100L);
      assertThat(variablesOfAllJobs(activated)).containsExactly(fitting);
      assertThat(jobKeysOf(activated)).containsExactly(100L);
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();

      // and - the failing job is reported for an incident
      assertThat(droppedJob)
          .hasValueSatisfying(
              dropped -> {
                assertThat(dropped).isInstanceOf(FailedInjectionJob.class);
                assertThat(dropped.jobKey()).isEqualTo(101L);
              });
    }

    @Test
    void shouldDropAndReportSingleJobWhoseInjectionFails() {
      // given - a single job whose variables are not valid msgpack
      final var response = batchWith(brokenVariablesJob(ref("token", "/auth")));
      final var activated = copyOf(response);

      // when
      final var droppedJob = inject(response, activated, Map.of("token", "resolved"));

      // then
      assertThat(jobKeysOf(response)).isEmpty();
      assertThat(jobKeysOf(activated)).isEmpty();
      assertThat(droppedJob)
          .hasValueSatisfying(
              dropped -> {
                assertThat(dropped).isInstanceOf(FailedInjectionJob.class);
                assertThat(dropped.jobKey()).isEqualTo(100L);
              });
    }

    @Test
    void shouldDropExceedingAndRemainingJobsFromBothBatches() {
      // given - the first job's value fits, the second job's value exceeds the remaining budget,
      // and a third job without secret references follows
      final var oversized = "x".repeat(EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER + 100);
      final Map<String, Object> fitting = Map.of("auth", "camunda.secrets.small");
      final var exceeding = Map.of("auth", "camunda.secrets.big");
      final var plain = Map.of("foo", "bar");
      final var response =
          batchWith(
              job(fitting, ref("small", "/auth")), job(exceeding, ref("big", "/auth")), job(plain));
      final var activated = copyOf(response);

      // when
      final var oversizedJob =
          inject(response, activated, Map.of("small", "resolved", "big", oversized));

      // then - the exceeding job and every job after it are dropped from both batches, even jobs
      // that would fit; the first job stays and only the response copy carries its secret value
      assertThat(variablesOfAllJobs(response)).containsExactly(Map.of("auth", "resolved"));
      assertThat(jobKeysOf(response)).containsExactly(100L);
      assertThat(variablesOfAllJobs(activated)).containsExactly(fitting);
      assertThat(jobKeysOf(activated)).containsExactly(100L);
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();

      // and - the dropped job is not reported for an incident: it was not first, so a next batch
      // with a fresh budget may still carry it
      assertThat(oversizedJob).isEmpty();
    }

    @Test
    void shouldDropJobWhenEarlierJobsConsumedTheGrowthBudget() {
      // given - each value fits into the budget on its own, but not both together
      final var firstValue = "x".repeat(EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER - 1000);
      final var secondValue = "y".repeat(2000);
      final Map<String, Object> first = Map.of("auth", "camunda.secrets.first");
      final var second = Map.of("auth", "camunda.secrets.second");
      final var response =
          batchWith(job(first, ref("first", "/auth")), job(second, ref("second", "/auth")));
      final var activated = copyOf(response);

      // when
      final var oversizedJob =
          inject(response, activated, Map.of("first", firstValue, "second", secondValue));

      // then - only the first job is activated with its value injected; the second is dropped
      // without an incident report
      assertThat(variablesOfAllJobs(response)).containsExactly(Map.of("auth", firstValue));
      assertThat(variablesOfAllJobs(activated)).containsExactly(first);
      assertThat(jobKeysOf(activated)).containsExactly(100L);
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();
      assertThat(oversizedJob).isEmpty();
    }

    @Test
    void shouldNotTruncateWhenInjectedValuesFit() {
      // given
      final var response =
          batchWith(
              job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")),
              job(Map.of("foo", "bar")));
      final var activated = copyOf(response);

      // when
      final var oversizedJob = inject(response, activated, Map.of("token", "resolved"));

      // then - nothing is dropped and the activated batch keeps its placeholders
      assertThat(variablesOfAllJobs(response))
          .containsExactly(Map.of("auth", "resolved"), Map.of("foo", "bar"));
      assertThat(variablesOfAllJobs(activated))
          .containsExactly(Map.of("auth", "camunda.secrets.token"), Map.of("foo", "bar"));
      assertThat(jobKeysOf(activated)).containsExactly(100L, 101L);
      assertThat(response.getTruncated()).isFalse();
      assertThat(activated.getTruncated()).isFalse();
      assertThat(oversizedJob).isEmpty();
    }

    @Test
    void shouldIgnoreReferenceWhenLeafIsNull() {
      // given - "=if flag then camunda.secrets.token else null" took the else branch
      final var original = new LinkedHashMap<String, Object>();
      original.put("t", null);
      final var batch = batchWith(job(original, ref("token", "/t")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("token", "resolved"));

      // then - no placeholder survives at /t, so the job activates untouched
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
    }

    @Test
    void shouldIgnoreReferenceWhenLeafIsANumber() {
      // given - "=if flag then camunda.secrets.retries else 3" took the else branch
      final var original = Map.of("t", 3);
      final var batch = batchWith(job(original, ref("retries", "/t")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("retries", "resolved"));

      // then
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
    }

    @Test
    void shouldIgnoreReferenceWhenLeafIsABooleanTrue() {
      // given - a leaf holding literal `true` must not be mistaken for a "placeholder replaced"
      // signal: Jackson caches BooleanNode.TRUE as a singleton, so an identity-based check for
      // that outcome could otherwise collide with a genuine boolean-true leaf
      final var original = Map.of("t", true);
      final var batch = batchWith(job(original, ref("token", "/t")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("token", "resolved"));

      // then
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
    }

    @Test
    void shouldInjectSiblingEntryWhenOtherEntryIsNull() {
      // given - a context whose entries mix a precise reference with a conditional one that
      // evaluated to null
      final var original = new LinkedHashMap<String, Object>();
      original.put("a", "camunda.secrets.x");
      original.put("b", null);
      final var wrapped =
          batchWith(job(Map.of("cfg", original), ref("x", "/cfg/a"), ref("y", "/cfg/b")));

      // when
      final var dropped = inject(wrapped, copyOf(wrapped), Map.of("x", "vx", "y", "vy"));

      // then - /cfg/a resolves; /cfg/b holds no placeholder so it is ignored
      assertThat(dropped).isEmpty();
      final var expected = new LinkedHashMap<String, Object>();
      expected.put("a", "vx");
      expected.put("b", null);
      assertThat(variablesOf(wrapped, 0)).isEqualTo(Map.of("cfg", expected));
    }

    @Test
    void shouldInjectEvaluatedBranchAndIgnoreTheOther() {
      // given - "=if cond then camunda.secrets.x else camunda.secrets.y", cond true
      final var batch =
          batchWith(job(Map.of("t", "camunda.secrets.x"), ref("x", "/t"), ref("y", "/t")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("x", "vx", "y", "vy"));

      // then - x resolves, y's branch never ran and leaves nothing behind
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("t", "vx"));
    }

    @Test
    void shouldInjectEvaluatedBranchRegardlessOfReferenceOrder() {
      // given - the mirror of the above, so the outcome cannot depend on which reference is
      // materialized first
      final var batch =
          batchWith(job(Map.of("t", "camunda.secrets.y"), ref("x", "/t"), ref("y", "/t")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("x", "vx", "y", "vy"));

      // then
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("t", "vy"));
    }

    @Test
    void shouldInjectOneOfThreeReferencesAtTheSamePath() {
      // given - nested conditionals put three references on one leaf
      final var batch =
          batchWith(
              job(
                  Map.of("t", "camunda.secrets.q"),
                  ref("p", "/t"),
                  ref("q", "/t"),
                  ref("r", "/t")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("p", "vp", "q", "vq", "r", "vr"));

      // then
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("t", "vq"));
    }

    @Test
    void shouldInjectEvaluatedBranchInsideSurroundingText() {
      // given - a conditional nested inside a concatenation
      final var batch =
          batchWith(
              job(
                  Map.of("auth", "Bearer camunda.secrets.devT"),
                  ref("prodT", "/auth"),
                  ref("devT", "/auth")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("prodT", "vp", "devT", "vd"));

      // then
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("auth", "Bearer vd"));
    }

    @Test
    void shouldIgnoreReferenceWhenLeafHoldsUnrelatedText() {
      // given - "=if true then \"localSecret\" else camunda.secrets.prod"
      final var original = Map.of("t", "localSecret");
      final var batch = batchWith(job(original, ref("prod", "/t")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("prod", "resolved"));

      // then
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
    }

    @Test
    void shouldIgnoreReferenceWhenAFunctionMangledThePlaceholder() {
      // given - "=upper case(camunda.secrets.token)" - the placeholder is no longer matchable
      // and no lowercase token survives either
      final var original = Map.of("t", "CAMUNDA.SECRETS.TOKEN");
      final var batch = batchWith(job(original, ref("token", "/t")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("token", "resolved"));

      // then
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
    }

    @Test
    void shouldIgnoreReferenceUsedOnlyInACondition() {
      // given - "=if camunda.secrets.flag = \"on\" then \"a\" else \"b\"" - the secret was
      // compared, never mapped
      final var original = Map.of("t", "b");
      final var batch = batchWith(job(original, ref("flag", "/t")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("flag", "resolved"));

      // then
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
    }

    @Test
    void shouldDropAndReportJobWhoseSurvivingPlaceholderNamesAnotherSecret() {
      // given - the leaf holds a stale placeholder for a secret the job no longer
      // references; a resolvable job precedes it and a plain job follows it
      final Map<String, Object> fitting = Map.of("auth", "camunda.secrets.token");
      final var response =
          batchWith(
              job(fitting, ref("token", "/auth")),
              job(Map.of("authToken", "camunda.secrets.tokenA"), ref("tokenB", "/authToken")),
              job(Map.of("foo", "bar")));
      final var activated = copyOf(response);

      // when
      final var dropped = inject(response, activated, Map.of("token", "resolved", "tokenB", "vb"));

      // then - the mismatched job and everything after it are dropped; the job before it keeps its
      // injected value on the response only
      assertThat(variablesOfAllJobs(response)).containsExactly(Map.of("auth", "resolved"));
      assertThat(jobKeysOf(response)).containsExactly(100L);
      assertThat(variablesOfAllJobs(activated)).containsExactly(fitting);
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();
      assertThat(dropped)
          .hasValueSatisfying(
              job -> {
                assertThat(job).isInstanceOf(FailedInjectionJob.class);
                assertThat(job.jobKey()).isEqualTo(101L);
                assertThat(((FailedInjectionJob) job).path()).isEqualTo("/authToken");
                assertThat(((FailedInjectionJob) job).placeholder())
                    .isEqualTo("camunda.secrets.tokenB");
              });
    }

    @Test
    void shouldDropJobWithOneMismatchedSecretAmongSeveralAtDifferentPaths() {
      // given - two references on the SAME job at different paths: one resolves, the other's
      // placeholder survives; a preceding job resolves cleanly and must not be swept up by the
      // later drop
      final Map<String, Object> preceding = Map.of("auth", "camunda.secrets.pre");
      final var response =
          batchWith(
              job(preceding, ref("pre", "/auth")),
              job(
                  Map.of("auth", "camunda.secrets.token", "key", "camunda.secrets.other"),
                  ref("token", "/auth"),
                  ref("apiKey", "/key")));
      final var activated = copyOf(response);

      // when
      final var dropped =
          inject(response, activated, Map.of("pre", "resolved-pre", "token", "t", "apiKey", "k"));

      // then - by the time /key's surviving placeholder is found, a genuine secret has already
      // been written into the live document at /auth; the whole job must still be dropped from
      // both batches so that partial write never reaches the response
      assertThat(variablesOfAllJobs(response)).containsExactly(Map.of("auth", "resolved-pre"));
      assertThat(jobKeysOf(response)).containsExactly(100L);
      assertThat(variablesOfAllJobs(activated)).containsExactly(preceding);
      assertThat(jobKeysOf(activated)).containsExactly(100L);
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();

      // and - the mismatched job is reported for an incident
      assertThat(dropped)
          .hasValueSatisfying(
              job -> {
                assertThat(job).isInstanceOf(FailedInjectionJob.class);
                assertThat(job.jobKey()).isEqualTo(101L);
              });
    }

    @Test
    void shouldDropJobWhenSiblingReferencesLeaveAPlaceholderBehind() {
      // given - two references at the path, neither resolves the placeholder actually there
      final var response =
          batchWith(job(Map.of("t", "camunda.secrets.tokenA"), ref("x", "/t"), ref("y", "/t")));
      final var activated = copyOf(response);

      // when
      final var dropped = inject(response, activated, Map.of("x", "vx", "y", "vy"));

      // then - siblings do not launder a surviving placeholder
      assertThat(jobKeysOf(response)).isEmpty();
      assertThat(jobKeysOf(activated)).isEmpty();
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();
      assertThat(dropped)
          .hasValueSatisfying(
              job -> {
                assertThat(job).isInstanceOf(FailedInjectionJob.class);
                assertThat(job.jobKey()).isEqualTo(100L);
                assertThat(((FailedInjectionJob) job).path()).isEqualTo("/t");
                // TimSort is stable and the fixture order is fixed, so "x" always wins today, but
                // that's an artifact of tie-breaking, not a contract - either sibling is correct
                assertThat(((FailedInjectionJob) job).placeholder())
                    .isIn("camunda.secrets.x", "camunda.secrets.y");
              });
    }

    @Test
    void shouldDropJobWhenIntermediateSegmentHoldsAPlaceholder() {
      // given - the recorded path descends into what is now a string, and that string holds
      // the placeholder
      final var batch =
          batchWith(job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth/nested")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("token", "resolved"));

      // then - the scalar the walk stopped at is what gets scanned
      assertThat(dropped)
          .hasValueSatisfying(
              job -> {
                assertThat(job).isInstanceOf(FailedInjectionJob.class);
                assertThat(((FailedInjectionJob) job).path()).isEqualTo("/auth/nested");
              });
      assertThat(jobKeysOf(batch)).isEmpty();
    }

    // ---- array pointers: injection can only write into an object, so an array hop always fails
    // closed, whether the index is in range or out of it ----

    @Test
    void shouldDropJobWhenPointerRunsIntoAnArrayWithANonNumericSegment() {
      // given - the recorded path descends into what is now a list, using a non-numeric segment;
      // an array missing the index must fail closed, the same as an object whose parent can't be
      // written to, rather than being read as an unset key
      final var response =
          batchWith(
              job(Map.of("foo", List.of("camunda.secrets.token")), ref("token", "/foo/nested")));
      final var activated = copyOf(response);

      // when
      final var dropped = inject(response, activated, Map.of("token", "resolved"));

      // then - the array itself is scanned, so the placeholder inside it is found
      assertThat(jobKeysOf(response)).isEmpty();
      assertThat(jobKeysOf(activated)).isEmpty();
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();
      assertThat(dropped)
          .hasValueSatisfying(
              job -> {
                assertThat(job).isInstanceOf(FailedInjectionJob.class);
                assertThat(job.jobKey()).isEqualTo(100L);
                assertThat(((FailedInjectionJob) job).path()).isEqualTo("/foo/nested");
                assertThat(((FailedInjectionJob) job).placeholder())
                    .isEqualTo("camunda.secrets.token");
              });
    }

    @Test
    void shouldDropJobWhenClusterVariableSecretIsNestedInAnArrayElement() {
      // given - the cluster-variable scanner does produce this shape: a secret nested inside a
      // SECRET_REFERENCE variable's list is recorded at an in-range array-index pointer (see
      // ClusterVariableSecretReferenceScanner), so the pointer reaches exactly the placeholder -
      // but injection can only write into an object via ObjectNode.put, so the array element can
      // never be written to, in range or not
      final var response =
          batchWith(
              job(Map.of("items", List.of("camunda.secrets.token")), ref("token", "/items/0")));
      final var activated = copyOf(response);

      // when
      final var dropped = inject(response, activated, Map.of("token", "resolved"));

      // then - the in-range element fails closed the same way an out-of-range one does
      assertThat(jobKeysOf(response)).isEmpty();
      assertThat(jobKeysOf(activated)).isEmpty();
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();
      assertThat(dropped)
          .hasValueSatisfying(
              job -> {
                assertThat(job).isInstanceOf(FailedInjectionJob.class);
                assertThat(job.jobKey()).isEqualTo(100L);
                assertThat(((FailedInjectionJob) job).path()).isEqualTo("/items/0");
                assertThat(((FailedInjectionJob) job).placeholder())
                    .isEqualTo("camunda.secrets.token");
              });
    }

    @Test
    void shouldDropJobWhenPointerIndexIsOutOfRangeForAList() {
      // given - the recorded path indexes past the end of what is now a shorter list
      final var response =
          batchWith(
              job(
                  Map.of("items", List.of("a", "camunda.secrets.token")),
                  ref("token", "/items/5")));
      final var activated = copyOf(response);

      // when
      final var dropped = inject(response, activated, Map.of("token", "resolved"));

      // then
      assertThat(jobKeysOf(response)).isEmpty();
      assertThat(jobKeysOf(activated)).isEmpty();
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();
      assertThat(dropped)
          .hasValueSatisfying(
              job -> {
                assertThat(job).isInstanceOf(FailedInjectionJob.class);
                assertThat(job.jobKey()).isEqualTo(100L);
                assertThat(((FailedInjectionJob) job).path()).isEqualTo("/items/5");
                assertThat(((FailedInjectionJob) job).placeholder())
                    .isEqualTo("camunda.secrets.token");
              });
    }

    @Test
    void shouldDropJobWhenComposedClusterVariablePointerOvershootsIntoAPlaceholder() {
      // given - "=string(camunda.vars.env.creds)" stringified the value, so the composed
      // pointer overshoots into a string that still holds the placeholder
      final var response =
          batchWith(job(Map.of("foo", "{token: camunda.secrets.X}"), ref("X", "/foo/token")));
      final var activated = copyOf(response);

      // when
      final var dropped = inject(response, activated, Map.of("X", "resolved"));

      // then
      assertThat(jobKeysOf(response)).isEmpty();
      assertThat(jobKeysOf(activated)).isEmpty();
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();
      assertThat(dropped)
          .hasValueSatisfying(
              job -> {
                assertThat(job).isInstanceOf(FailedInjectionJob.class);
                assertThat(job.jobKey()).isEqualTo(100L);
                assertThat(((FailedInjectionJob) job).path()).isEqualTo("/foo/token");
                assertThat(((FailedInjectionJob) job).placeholder()).isEqualTo("camunda.secrets.X");
              });
    }

    @Test
    void shouldDropJobWhenLeafIsAListOfPlaceholders() {
      // given - a list literal or a "for ... return" produced an array whose elements are
      // placeholders
      final var batch =
          batchWith(
              job(
                  Map.of("foo", List.of("camunda.secrets.X", "camunda.secrets.Y")),
                  ref("X", "/foo"),
                  ref("Y", "/foo")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("X", "vx", "Y", "vy"));

      // then - array elements are never addressed, and the placeholders inside are found
      assertThat(dropped)
          .hasValueSatisfying(
              job -> {
                assertThat(job).isInstanceOf(FailedInjectionJob.class);
                assertThat(((FailedInjectionJob) job).path()).isEqualTo("/foo");
              });
      assertThat(jobKeysOf(batch)).isEmpty();
    }

    @Test
    void shouldDropJobWhenLeafIsAnObjectHoldingAPlaceholder() {
      // given - the pointer addresses a container whose contents hold the placeholder
      final var response =
          batchWith(job(Map.of("cfg", Map.of("a", "camunda.secrets.token")), ref("token", "/cfg")));
      final var activated = copyOf(response);

      // when
      final var dropped = inject(response, activated, Map.of("token", "resolved"));

      // then
      assertThat(jobKeysOf(response)).isEmpty();
      assertThat(jobKeysOf(activated)).isEmpty();
      assertThat(response.getTruncated()).isTrue();
      assertThat(activated.getTruncated()).isTrue();
      assertThat(dropped)
          .hasValueSatisfying(
              job -> {
                assertThat(job).isInstanceOf(FailedInjectionJob.class);
                assertThat(job.jobKey()).isEqualTo(100L);
                assertThat(((FailedInjectionJob) job).path()).isEqualTo("/cfg");
                assertThat(((FailedInjectionJob) job).placeholder())
                    .isEqualTo("camunda.secrets.token");
              });
    }

    @Test
    void shouldLeaveUnclaimedLookalikeTextBesideAResolvedReference() {
      // given - the leaf holds a second placeholder-shaped token that no reference claims; it
      // is literal customer text, not a reference
      final var batch =
          batchWith(job(Map.of("t", "camunda.secrets.x and camunda.secrets.z"), ref("x", "/t")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("x", "vx"));

      // then - x resolves and nothing at /t is unsatisfied, so no scan runs and z passes through
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("t", "vx and camunda.secrets.z"));
    }

    @Test
    void shouldIgnoreReferenceWhoseKeyWasNeverMapped() {
      // given - a secret was added to a cluster variable after the input mapping ran, so its
      // key is not in the job's variables at all
      final var batch =
          batchWith(
              job(
                  Map.of("creds", Map.of("a", "camunda.secrets.X")),
                  ref("X", "/creds/a"),
                  ref("Y", "/creds/b")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("X", "vx", "Y", "vy"));

      // then - /creds/b is simply unset, so it is absent rather than unsatisfied
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("creds", Map.of("a", "vx")));
    }

    @Test
    void shouldLeaveOrphanedPlaceholderWhenNoReferenceClaimsIt() {
      // given - a secret was removed from a cluster variable after the input mapping baked
      // both placeholders in, so only one reference reaches the job. Characterises today's
      // behavior: the orphan passes through, because no reference at /creds/b is unsatisfied
      final var batch =
          batchWith(
              job(
                  Map.of("creds", Map.of("a", "camunda.secrets.X", "b", "camunda.secrets.Y")),
                  ref("X", "/creds/a")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("X", "vx"));

      // then - known gap: fixing this needs the reference set and the baked text to come from one
      // read of ClusterVariableState (tracked separately)
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0))
          .isEqualTo(Map.of("creds", Map.of("a", "vx", "b", "camunda.secrets.Y")));
    }

    @Test
    void shouldInjectAdjacentPlaceholdersWithoutTokenisingThem() {
      // given - two placeholders concatenated with no separator. They do not tokenise cleanly
      // (the name class is greedy), which is why the residual scan reads the injected result
      final var batch =
          batchWith(
              job(
                  Map.of("combined", "camunda.secrets.acamunda.secrets.b"),
                  ref("a", "/combined"),
                  ref("b", "/combined")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("a", "VA", "b", "VB"));

      // then - both resolve and no incident is raised for the unmatchable token shape
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("combined", "VAVB"));
    }

    @Test
    void shouldNotIncidentOnAdjacentPlaceholdersWhenAThirdReferenceIsUnsatisfied() {
      // given - the regression the post-injection scan exists for: two adjacent placeholders both
      // resolve, and a third reference at the same path finds nothing
      final var batch =
          batchWith(
              job(
                  Map.of("combined", "camunda.secrets.acamunda.secrets.b"),
                  ref("a", "/combined"),
                  ref("b", "/combined"),
                  ref("c", "/combined")));

      // when
      final var dropped = inject(batch, copyOf(batch), Map.of("a", "VA", "b", "VB", "c", "VC"));

      // then - nothing placeholder-shaped survives, so c's absence is not a failure
      assertThat(dropped).isEmpty();
      assertThat(variablesOf(batch, 0)).isEqualTo(Map.of("combined", "VAVB"));
    }

    @Test
    void shouldSkipWhenLeafIsMissing() {
      // given
      final var original = Map.of("present", "camunda.secrets.token");
      final var batch = batchWith(job(original, ref("token", "/absent")));

      // when
      inject(batch, Map.of("token", "resolved"));

      // then
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
    }

    @Test
    void shouldSkipWhenPointerHasNoLeadingSlash() {
      // given - an empty pointer is not a valid leaf pointer
      final var original = Map.of("authorization", "Bearer camunda.secrets.token");
      final var batch = batchWith(job(original, ref("token", "")));

      // when
      inject(batch, Map.of("token", "resolved"));

      // then
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
    }

    private static void inject(final JobBatchRecord batch, final Map<String, String> secrets) {
      inject(batch, copyOf(batch), secrets);
    }

    /**
     * Mirrors the processor flow: register the jobs of the to-be-activated batch like the
     * collector, then inject the prepared values into the response.
     *
     * <p>Sizes the message-size limit at the batch length plus twice the calculation buffer, so the
     * effective growth the first job may add before it is dropped is exactly one {@link
     * EngineConfiguration#BATCH_SIZE_CALCULATION_BUFFER} (the injector keeps one buffer as the
     * framing margin on top of the growth), matching what these drop tests exercise.
     */
    private static Optional<DroppedJob> inject(
        final JobBatchRecord response,
        final JobBatchRecord activated,
        final Map<String, String> secrets) {
      final var injector = injector(secrets);
      injector.reset();
      int index = 0;
      for (final JobRecord job : activated.jobs()) {
        injector.registerForInjection(injector.checkSecrets(job), index, job);
        index++;
      }
      final int baseLength = activated.getLength();
      final int maxMessageSize = baseLength + 2 * EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
      return injector.injectSecretValues(
          response, activated, baseLength, length -> length <= maxMessageSize);
    }
  }

  private record SecretRef(String storeId, String name, String path) {}
}
