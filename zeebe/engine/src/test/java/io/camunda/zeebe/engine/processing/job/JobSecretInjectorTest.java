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

  private static final String STORE_ID = "";

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
    final Map<String, List<Long>> byName = new LinkedHashMap<>();
    injector
        .jobsWithNonCachedSecrets()
        .forEach((reference, keys) -> byName.put(reference.name(), keys));
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
      injector.injectSecretValues(response, batch);
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
      injector.injectSecretValues(response, batch);
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
          .extracting(secret -> secret.reference().name())
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
      injector.injectSecretValues(response, batch);
      assertThat(variablesOf(response, 0)).isEqualTo(Map.of("auth", "resolved"));
    }

    @Test
    void shouldNotResolveReferenceWithoutStoreIdWhenSeveralStoresAreConfigured() {
      // given - both stores cache the secret, but the reference names no store, which is
      // ambiguous with more than one configured store
      final var cacheA = new InMemorySecretCache();
      cacheA.put("token", "a");
      final var cacheB = new InMemorySecretCache();
      cacheB.put("token", "b");
      final var registry =
          new SecretStoreRegistry(
              Map.of("store-a", new NoopSecretStore(), "store-b", new NoopSecretStore()),
              Map.of("store-a", cacheA, "store-b", cacheB));
      final var injector = new JobSecretInjector(registry);

      // when
      final var batch =
          collect(injector, job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")));

      // then - the job is skipped instead of guessing a store
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
    void shouldSkipWhenPointerDoesNotAddressATextLeaf() {
      // given - the pointer addresses a container, not a text leaf
      final var original = Map.of("cfg", Map.of("a", "camunda.secrets.token"));
      final var batch = batchWith(job(original, ref("token", "/cfg")));

      // when
      inject(batch, Map.of("token", "resolved"));

      // then - defensively left untouched
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
    }

    @Test
    void shouldSkipWhenIntermediateSegmentIsMissing() {
      // given - the recorded path no longer matches the variables document
      final var original = Map.of("auth", "camunda.secrets.token");
      final var batch = batchWith(job(original, ref("token", "/auth/nested")));

      // when
      inject(batch, Map.of("token", "resolved"));

      // then - defensively left untouched
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
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

    @Test
    void shouldSkipArraysOnThePointerPath() {
      // given - the pointer runs into an array element
      final var original = Map.of("items", List.of("camunda.secrets.token"));
      final var batch = batchWith(job(original, ref("token", "/items/0")));

      // when
      inject(batch, Map.of("token", "resolved"));

      // then - array elements are never addressed; the document is untouched
      assertThat(variablesOf(batch, 0)).isEqualTo(original);
    }

    private static void inject(final JobBatchRecord batch, final Map<String, String> secrets) {
      inject(batch, copyOf(batch), secrets);
    }

    /**
     * Mirrors the processor flow: register the jobs of the to-be-activated batch like the
     * collector, then inject the prepared values into the response.
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
      return injector.injectSecretValues(response, activated);
    }
  }

  private record SecretRef(String storeId, String name, String path) {}
}
