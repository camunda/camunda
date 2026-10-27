/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.jobstream;

import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.shared.management.JobStreamEndpoint.ClientJobStream;
import io.camunda.zeebe.shared.management.JobStreamEndpoint.JobStream;
import io.camunda.zeebe.shared.management.JobStreamEndpoint.RemoteJobStream;
import io.camunda.zeebe.shared.management.JobStreamEndpoint.RemoteStreamId;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractCollectionAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.condition.AllOf;
import org.assertj.core.condition.VerboseCondition;

@SuppressWarnings("UnusedReturnValue")
public abstract class AbstractJobStreamsAssert<
        SELF extends AbstractJobStreamsAssert<SELF, T>, T extends JobStream>
    extends AbstractCollectionAssert<SELF, Collection<T>, T, ObjectAssert<T>> {

  public AbstractJobStreamsAssert(final Collection<T> actual, final Class<?> selfType) {
    super(actual, selfType);
  }

  /**
   * Asserts that the given service contains exactly {@code expectedCount} streams for the given job
   * type.
   *
   * @param expectedCount the exact count of streams to find
   * @param jobType the expected type of the streams
   * @return itself for chaining
   */
  public SELF haveJobType(final int expectedCount, final String jobType) {
    return haveExactly(expectedCount, hasJobType(jobType));
  }

  /**
   * Asserts that the given service contains exactly {@code expectedCount} streams for the given
   * worker name.
   *
   * @param expectedCount the exact count of streams to find
   * @param worker the expected worker property of the streams
   * @return itself for chaining
   */
  public SELF haveWorker(final int expectedCount, final String worker) {
    return haveExactly(expectedCount, hasWorker(worker));
  }

  /**
   * Asserts that the given service does not contain any streams for the given worker name.
   *
   * @param worker the expected worker of the streams
   * @return itself for chaining
   */
  public SELF doNotHaveWorker(final String worker) {
    return doNotHave(hasWorker(worker));
  }

  /**
   * Asserts that the given service contains exactly {@code expectedCount} streams which matches all
   * the given conditions.
   *
   * @param expectedCount the exact count of streams to find
   * @param conditions all the condition the streams must match
   * @return itself for chaining
   */
  @SafeVarargs
  public final SELF haveExactlyAll(
      final int expectedCount, final Condition<? super T>... conditions) {
    return haveExactly(expectedCount, AllOf.allOf(conditions));
  }

  /**
   * Asserts that the given service does not contain any streams for the given stream type.
   *
   * @param jobType the expected job type of the streams
   * @return itself for chaining
   */
  public SELF doNotHaveJobType(final String jobType) {
    return doNotHave(hasJobType(jobType));
  }

  @Override
  protected ObjectAssert<T> toAssert(final T value, final String description) {
    return Assertions.assertThat(value).as(description);
  }

  /** Returns a condition which checks that a stream has the given stream type. */
  public static <T extends JobStream> Condition<T> hasJobType(final String streamType) {
    return VerboseCondition.verboseCondition(
        stream -> stream.jobType().equals(streamType),
        "a stream with type '%s'".formatted(streamType),
        stream -> " but actual type is '%s'".formatted(stream.jobType()));
  }

  /** Returns a condition which checks that a stream has the given worker name. */
  public static <T extends JobStream> Condition<T> hasWorker(final String workerName) {
    return VerboseCondition.verboseCondition(
        stream -> stream.metadata().worker().equals(workerName),
        "a stream with worker '%s'".formatted(workerName),
        stream -> " but actual worker is '%s'".formatted(stream.metadata().worker()));
  }

  /** Returns a condition which checks that a stream is connected to the given brokers by ID. */
  public static Condition<ClientJobStream> isConnectedTo(final int... nodeId) {
    final var members = Arrays.stream(nodeId).boxed().collect(Collectors.toSet());
    return VerboseCondition.verboseCondition(
        stream -> stream.connectedTo().containsAll(members),
        "a stream connected to brokers %s".formatted(Arrays.toString(nodeId)),
        stream -> " but actual connections are '%s'".formatted(stream.connectedTo()));
  }

  /** Returns a condition which checks that a stream has the given timeout in milliseconds. */
  public static <T extends JobStream> Condition<T> hasTimeout(final long timeoutMillis) {
    return VerboseCondition.verboseCondition(
        stream -> stream.metadata().timeout().toMillis() == timeoutMillis,
        "a stream with timeout '%dms'".formatted(timeoutMillis),
        stream -> " but actual timeout is '%s'".formatted(stream.metadata().timeout()));
  }

  /** Returns a condition which checks that a stream has the given timeout in milliseconds. */
  public static <T extends JobStream> Condition<T> hasFetchVariables(final String... variables) {
    final var expectedVariables = Arrays.asList(variables);
    return VerboseCondition.verboseCondition(
        stream ->
            stream.metadata().fetchVariables().containsAll(expectedVariables)
                && stream.metadata().fetchVariables().size() == expectedVariables.size(),
        "a stream with fetch variables %s".formatted(Arrays.toString(variables)),
        stream -> " but actual variables is %s".formatted(stream.metadata().fetchVariables()));
  }

  /** Returns a condition which checks that a stream has the given tenant filter. */
  public static <T extends JobStream> Condition<T> hasTenantFilter(
      final TenantFilter tenantFilter) {
    return VerboseCondition.verboseCondition(
        stream -> stream.metadata().tenantFilter() == tenantFilter,
        "a stream with tenant filter '%s'".formatted(tenantFilter),
        stream -> " but actual tenant filter is '%s'".formatted(stream.metadata().tenantFilter()));
  }

  /** Returns a condition which checks that a stream has the expected amount of consumers. */
  public static Condition<RemoteJobStream> hasConsumerCount(final int count) {
    return VerboseCondition.verboseCondition(
        stream -> stream.consumers().size() == count,
        "a stream with '%d' consumers".formatted(count),
        stream -> " but actual consumers are '%s'".formatted(stream.consumers()));
  }

  /**
   * Returns a condition which checks that a stream consumers contains exactly the given receivers,
   * in any order.
   */
  public static Condition<RemoteJobStream> hasConsumerReceivers(
      final Collection<String> receivers) {
    return VerboseCondition.verboseCondition(
        stream ->
            stream.consumers().size() == receivers.size()
                && stream.consumers().stream()
                    .map(RemoteStreamId::receiver)
                    .collect(Collectors.toSet())
                    .containsAll(receivers),
        "a stream with consumer receivers '%s'".formatted(receivers),
        stream -> " but actual consumers are '%s'".formatted(stream.consumers()));
  }
}
