/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.job.JobSecretInjector.OversizedJob;
import io.camunda.zeebe.engine.processing.job.SecretResolver.SecretReference;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class JobSecretInjectorTest {

  private static final String STORE_ID = "";

  @Nested
  final class RemoveJobsWithUncachedSecrets {

    @Test
    void shouldRemoveJobWhoseSecretIsNotCached() {
      // given - a cached job, an uncached job, and a job without references
      final var injector = injector(Map.of("token", "resolved"));
      final var batch =
          batchWith(
              job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")),
              job(Map.of("auth", "camunda.secrets.other"), ref("other", "/auth")),
              job(Map.of("foo", "bar")));

      // when
      final var preparation = injector.removeJobsWithUncachedSecrets(batch);

      // then - the uncached job and its key are removed, the others stay aligned
      assertThat(variablesOfAllJobs(batch))
          .containsExactly(Map.of("auth", "camunda.secrets.token"), Map.of("foo", "bar"));
      assertThat(jobKeysOf(batch)).containsExactly(100L, 102L);
      assertThat(preparation.values())
          .containsEntry(new SecretReference(STORE_ID, "token"), "resolved");
    }

    @Test
    void shouldKeepAllJobsWhenAllSecretsAreCached() {
      // given
      final var injector = injector(Map.of("token", "t", "apiKey", "k"));
      final var batch =
          batchWith(
              job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")),
              job(Map.of("key", "camunda.secrets.apiKey"), ref("apiKey", "/key")));

      // when
      final var preparation = injector.removeJobsWithUncachedSecrets(batch);

      // then
      assertThat(jobKeysOf(batch)).containsExactly(100L, 101L);
      assertThat(preparation.values())
          .containsEntry(new SecretReference(STORE_ID, "token"), "t")
          .containsEntry(new SecretReference(STORE_ID, "apiKey"), "k");
    }

    @Test
    void shouldRemoveJobWhenOnlySomeOfItsSecretsAreCached() {
      // given - one of the job's two references has no cached value
      final var injector = injector(Map.of("token", "t"));
      final var batch =
          batchWith(
              job(
                  Map.of("auth", "camunda.secrets.token", "key", "camunda.secrets.apiKey"),
                  ref("token", "/auth"),
                  ref("apiKey", "/key")));

      // when
      injector.removeJobsWithUncachedSecrets(batch);

      // then
      assertThat(jobKeysOf(batch)).isEmpty();
      assertThat(variablesOfAllJobs(batch)).isEmpty();
    }

    @Test
    void shouldRemoveNonAdjacentJobsAndKeepKeysAligned() {
      // given - uncached jobs at index 0 and 2
      final var injector = injector(Map.of("token", "t"));
      final var batch =
          batchWith(
              job(Map.of("a", "camunda.secrets.other"), ref("other", "/a")),
              job(Map.of("b", "camunda.secrets.token"), ref("token", "/b")),
              job(Map.of("c", "camunda.secrets.another"), ref("another", "/c")),
              job(Map.of("d", "plain")));

      // when
      injector.removeJobsWithUncachedSecrets(batch);

      // then
      assertThat(variablesOfAllJobs(batch))
          .containsExactly(Map.of("b", "camunda.secrets.token"), Map.of("d", "plain"));
      assertThat(jobKeysOf(batch)).containsExactly(101L, 103L);
    }

    @Test
    void shouldRemoveAllSecretJobsWhenResolverThrows() {
      // given
      final SecretResolver throwingResolver =
          references -> {
            throw new IllegalStateException("resolver is broken");
          };
      final var batch =
          batchWith(
              job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")),
              job(Map.of("foo", "bar")));

      // when
      final var preparation =
          new JobSecretInjector(throwingResolver).removeJobsWithUncachedSecrets(batch);

      // then - only the job without references stays
      assertThat(variablesOfAllJobs(batch)).containsExactly(Map.of("foo", "bar"));
      assertThat(jobKeysOf(batch)).containsExactly(101L);
      assertThat(preparation.values()).isEmpty();
      assertThat(preparation.pendingJobs()).isEmpty();
    }

    @Test
    void shouldRemoveSecretJobsWithNoopResolver() {
      // given - the noop resolver has no cached values
      final var batch =
          batchWith(
              job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")),
              job(Map.of("foo", "bar")));

      // when
      new JobSecretInjector(SecretResolver.noop()).removeJobsWithUncachedSecrets(batch);

      // then
      assertThat(variablesOfAllJobs(batch)).containsExactly(Map.of("foo", "bar"));
    }

    @Test
    void shouldNotTouchBatchWithoutSecretReferences() {
      // given
      final var injector = injector(Map.of());
      final var batch = batchWith(job(Map.of("foo", "bar")), job(Map.of("baz", "qux")));

      // when
      final var preparation = injector.removeJobsWithUncachedSecrets(batch);

      // then
      assertThat(jobKeysOf(batch)).containsExactly(100L, 101L);
      assertThat(preparation.values()).isEmpty();
      assertThat(preparation.pendingJobs()).isEmpty();
      assertThat(preparation.missingSecrets()).isEmpty();
    }

    @Test
    void shouldReportNoMissingSecretsWhenAllCached() {
      // given
      final var injector = injector(Map.of("token", "t"));
      final var batch =
          batchWith(job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")));

      // when
      final var preparation = injector.removeJobsWithUncachedSecrets(batch);

      // then
      assertThat(preparation.missingSecrets()).isEmpty();
    }

    @Test
    void shouldGroupMissingSecretByReferenceWithWaitingJobKeys() {
      // given - two jobs waiting for the same uncached secret, one for a different uncached secret
      final var injector = injector(Map.of());
      final var batch =
          batchWith(
              job(Map.of("a", "camunda.secrets.token"), ref("token", "/a")),
              job(Map.of("b", "camunda.secrets.token"), ref("token", "/b")),
              job(Map.of("c", "camunda.secrets.other"), ref("other", "/c")));

      // when
      final var preparation = injector.removeJobsWithUncachedSecrets(batch);

      // then - one entry per reference, carrying the keys of the jobs that await it
      assertThat(preparation.missingSecrets())
          .containsOnly(
              entry(new SecretReference(STORE_ID, "token"), List.of(100L, 101L)),
              entry(new SecretReference(STORE_ID, "other"), List.of(102L)));
      assertThat(jobKeysOf(batch)).isEmpty();
    }

    @Test
    void shouldReportOnlyUncachedReferencesOfRemovedJob() {
      // given - a job with one cached and one uncached reference
      final var injector = injector(Map.of("token", "t"));
      final var batch =
          batchWith(
              job(
                  Map.of("auth", "camunda.secrets.token", "key", "camunda.secrets.apiKey"),
                  ref("token", "/auth"),
                  ref("apiKey", "/key")));

      // when
      final var preparation = injector.removeJobsWithUncachedSecrets(batch);

      // then - only the uncached reference is requested
      assertThat(preparation.missingSecrets())
          .containsExactly(entry(new SecretReference(STORE_ID, "apiKey"), List.of(100L)));
    }

    @Test
    void shouldReportJobKeyOnceForReferenceRepeatedWithinAJob() {
      // given - one job referencing the same uncached secret at two paths
      final var injector = injector(Map.of());
      final var batch =
          batchWith(
              job(
                  Map.of("a", "camunda.secrets.token", "b", "camunda.secrets.token"),
                  ref("token", "/a"),
                  ref("token", "/b")));

      // when
      final var preparation = injector.removeJobsWithUncachedSecrets(batch);

      // then - the job key appears once for the shared reference, not once per path
      assertThat(preparation.missingSecrets())
          .containsExactly(entry(new SecretReference(STORE_ID, "token"), List.of(100L)));
    }

    @Test
    void shouldReportEachUncachedReferenceOfASingleJobWithItsKey() {
      // given - one job waiting on two distinct uncached secrets
      final var injector = injector(Map.of());
      final var batch =
          batchWith(
              job(
                  Map.of("a", "camunda.secrets.token", "b", "camunda.secrets.apiKey"),
                  ref("token", "/a"),
                  ref("apiKey", "/b")));

      // when
      final var preparation = injector.removeJobsWithUncachedSecrets(batch);

      // then - one entry per reference, each carrying the job's key
      assertThat(preparation.missingSecrets())
          .containsOnly(
              entry(new SecretReference(STORE_ID, "token"), List.of(100L)),
              entry(new SecretReference(STORE_ID, "apiKey"), List.of(100L)));
    }

    @Test
    void shouldReportAllReferencesMissingWhenResolverThrows() {
      // given
      final SecretResolver throwingResolver =
          references -> {
            throw new IllegalStateException("resolver is broken");
          };
      final var batch =
          batchWith(
              job(Map.of("auth", "camunda.secrets.token"), ref("token", "/auth")),
              job(Map.of("key", "camunda.secrets.apiKey"), ref("apiKey", "/key")));

      // when
      final var preparation =
          new JobSecretInjector(throwingResolver).removeJobsWithUncachedSecrets(batch);

      // then - every reference is requested for background resolution
      assertThat(preparation.missingSecrets())
          .containsOnly(
              entry(new SecretReference(STORE_ID, "token"), List.of(100L)),
              entry(new SecretReference(STORE_ID, "apiKey"), List.of(101L)));
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
    void shouldInjectOnlyFullyCachedJobsInMultiJobBatch() {
      // given - a cached job, a job without references, and a job with an uncached reference
      final Map<String, Object> withSecret = Map.of("auth", "camunda.secrets.token");
      final Map<String, Object> withoutSecret = Map.of("foo", "bar");
      final Map<String, Object> uncached = Map.of("auth", "camunda.secrets.other");
      final var activated =
          batchWith(
              job(withSecret, ref("token", "/auth")),
              job(withoutSecret),
              job(uncached, ref("other", "/auth")));

      // when - mirroring the processor: remove the uncached jobs, then inject into a response copy
      final var injector = injector(Map.of("token", "resolved"));
      final var preparation = injector.removeJobsWithUncachedSecrets(activated);
      final var response = copyOf(activated);
      injector.injectSecretValues(response, activated, preparation);

      // then - the uncached job is removed and only the first job gets its value injected
      assertThat(variablesOfAllJobs(response))
          .containsExactly(Map.of("auth", "resolved"), withoutSecret);
      assertThat(variablesOfAllJobs(activated)).containsExactly(withSecret, withoutSecret);
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
              job -> {
                assertThat(job.jobKey()).isEqualTo(100L);
                assertThat(job.growth())
                    .isGreaterThan(EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER);
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
     * Mirrors the processor flow: prepare on the to-be-activated batch, inject into the response.
     */
    private static Optional<OversizedJob> inject(
        final JobBatchRecord response,
        final JobBatchRecord activated,
        final Map<String, String> secrets) {
      final var injector = injector(secrets);
      final var preparation = injector.removeJobsWithUncachedSecrets(activated);
      return injector.injectSecretValues(response, activated, preparation);
    }
  }

  private static JobSecretInjector injector(final Map<String, String> cachedSecrets) {
    return new JobSecretInjector(
        references -> {
          final Map<SecretReference, String> values = new HashMap<>();
          for (final SecretReference reference : references) {
            final String value = cachedSecrets.get(reference.secretReference());
            if (value != null) {
              values.put(reference, value);
            }
          }
          return values;
        });
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

  private record SecretRef(String storeId, String name, String path) {}
}
