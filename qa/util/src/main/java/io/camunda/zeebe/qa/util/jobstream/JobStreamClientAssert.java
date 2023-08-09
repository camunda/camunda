/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.qa.util.jobstream;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.gateway.impl.stream.JobStreamClient;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationProperties;
import io.camunda.zeebe.transport.stream.api.ClientStream;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.assertj.core.condition.AllOf;
import org.assertj.core.condition.VerboseCondition;

/**
 * Convenience class to assert certain properties of a {@link JobStreamClient}, e.g. is a stream
 * registered with the given properties, how many streams there are, etc.
 *
 * <p>NOTE: while there's a lot of similar code between this and {@link JobStreamServiceAssert} -
 * especially the conditions - since the actual values under assertion do not share a common type,
 * it's likely not worth the effort of deduplication.
 */
public class JobStreamClientAssert
    extends AbstractObjectAssert<JobStreamClientAssert, JobStreamClient> {
  /**
   * @param jobStreamClient the actual client to assert against
   */
  public JobStreamClientAssert(final JobStreamClient jobStreamClient) {
    super(jobStreamClient, JobStreamClientAssert.class);
  }

  /**
   * A convenience factory method that's consistent with AssertJ conventions.
   *
   * @param actual the actual client to assert against
   * @return an instance of {@link JobStreamClientAssert} to assert properties of the given client
   */
  public static JobStreamClientAssert assertThat(final JobStreamClient actual) {
    return new JobStreamClientAssert(actual);
  }

  /**
   * Asserts that the given client contains exactly {@code expectedCount} streams for the given job
   * type.
   *
   * @param expectedCount the exact count of streams to find
   * @param jobType the expected type of the streams
   * @return itself for chaining
   */
  public JobStreamClientAssert hasStreamWithType(final int expectedCount, final String jobType) {
    return hasStreamMatchingCount(expectedCount, hasStreamType(jobType));
  }

  /**
   * Asserts that the given client contains exactly {@code expectedCount} streams for the given job
   * type.
   *
   * @param expectedCount the exact count of streams to find
   * @param jobType the expected type of the streams
   * @return itself for chaining
   */
  public JobStreamClientAssert hasStreamWithType(
      final int expectedCount, final String jobType, final int... connectedTo) {
    return hasStreamMatchingCount(
        expectedCount, AllOf.allOf(hasStreamType(jobType), isConnectedTo(connectedTo)));
  }

  /**
   * Asserts that the given client contains exactly {@code expectedCount} streams for the given
   * worker name.
   *
   * @param expectedCount the exact count of streams to find
   * @param worker the expected worker property of the streams
   * @return itself for chaining
   */
  public JobStreamClientAssert hasStreamWithWorker(final int expectedCount, final String worker) {
    return hasStreamMatchingCount(expectedCount, hasWorker(worker));
  }

  /**
   * Asserts that the given client does not contain any streams for the given worker name.
   *
   * @param worker the expected worker of the streams
   * @return itself for chaining
   */
  public JobStreamClientAssert doesNotHaveStreamWithWorker(final String worker) {
    return doesNotHaveStreamMatching(hasWorker(worker));
  }

  /**
   * Asserts that the given client does not contain any streams for the given stream type.
   *
   * @param streamType the expected job type of the streams
   * @return itself for chaining
   */
  public JobStreamClientAssert doesNotHaveStreamWithType(final String streamType) {
    return doesNotHaveStreamMatching(hasStreamType(streamType));
  }

  /**
   * Asserts that the given client contains exactly {@code expectedCount} streams which all match
   * the same condition.
   *
   * <p>NOTE: you can reuse the conditions found as static methods of this class, e.g. {@link
   * #hasWorker(String)}, {@link #hasStreamType(String)}. If you wish to combine them, you can use
   * {@link org.assertj.core.condition.AnyOf} or {@link org.assertj.core.condition.AllOf}.
   *
   * @param expectedCount the exact count of streams to find
   * @param condition the condition the streams must match
   * @return itself for chaining
   */
  public JobStreamClientAssert hasStreamMatchingCount(
      final int expectedCount, final Condition<ClientStream<JobActivationProperties>> condition) {
    isNotNull();

    final var description =
        descriptionOrDefault("has at '%d' stream(s) matching assertions", expectedCount);
    final var streams = actual.list().join();
    Assertions.assertThat(streams).as(description).haveExactly(expectedCount, condition);

    return myself;
  }

  /**
   * Asserts that the given client does not container any stream which matches the given condition.
   *
   * <p>NOTE: you can reuse the conditions found as static methods of this class, e.g. {@link
   * #hasWorker(String)}, {@link #hasStreamType(String)}. If you wish to combine them, you can use
   * {@link org.assertj.core.condition.AnyOf} or {@link org.assertj.core.condition.AllOf}.
   *
   * @param condition the condition the streams must match
   * @return itself for chaining
   */
  public JobStreamClientAssert doesNotHaveStreamMatching(
      final Condition<ClientStream<JobActivationProperties>> condition) {
    isNotNull();

    final var description = descriptionOrDefault("has no streams matching assertions");
    final var streams = actual.list().join();
    Assertions.assertThat(streams).as(description).doNotHave(condition);

    return myself;
  }

  /** Returns a condition which checks that a stream has the given stream type. */
  public static Condition<ClientStream<JobActivationProperties>> hasStreamType(
      final String streamType) {
    return VerboseCondition.verboseCondition(
        stream -> BufferUtil.bufferAsString(stream.streamType()).equals(streamType),
        "a stream with type '%s'".formatted(streamType),
        stream ->
            " but actual type is '%s'".formatted(BufferUtil.bufferAsString(stream.streamType())));
  }

  /** Returns a condition which checks that a stream has the given worker name. */
  public static Condition<ClientStream<JobActivationProperties>> hasWorker(
      final String workerName) {
    return VerboseCondition.verboseCondition(
        stream -> BufferUtil.bufferAsString(stream.metadata().worker()).equals(workerName),
        "a stream with worker '%s'".formatted(workerName),
        stream ->
            " but actual worker is '%s'"
                .formatted(BufferUtil.bufferAsString(stream.metadata().worker())));
  }

  /** Returns a condition which checks that a stream is connected to the given brokers by ID. */
  public static Condition<ClientStream<JobActivationProperties>> isConnectedTo(
      final int... nodeId) {
    final var members =
        Arrays.stream(nodeId)
            .mapToObj(String::valueOf)
            .map(MemberId::from)
            .collect(Collectors.toSet());
    return VerboseCondition.verboseCondition(
        stream -> {
          members.removeAll(stream.liveConnections());
          return members.isEmpty();
        },
        "a stream connected to brokers %s".formatted(Arrays.toString(nodeId)),
        stream -> " but actual connections are '%s'".formatted(stream.liveConnections()));
  }

  /** Returns a condition which checks that a stream has the given timeout in milliseconds. */
  public static Condition<ClientStream<JobActivationProperties>> hasTimeout(
      final long timeoutMillis) {
    return VerboseCondition.verboseCondition(
        stream -> stream.metadata().timeout() == timeoutMillis,
        "a stream with timeout '%dms'".formatted(timeoutMillis),
        stream -> " but actual timeout is '%dms'".formatted(stream.metadata().timeout()));
  }

  /** Returns a condition which checks that a stream has the given timeout in milliseconds. */
  public static Condition<ClientStream<JobActivationProperties>> hasFetchVariables(
      final String... variables) {
    //noinspection DuplicatedCode
    final var expectedVariables = Arrays.stream(variables).map(BufferUtil::wrapString).toList();
    return VerboseCondition.verboseCondition(
        stream ->
            stream.metadata().fetchVariables().containsAll(expectedVariables)
                && stream.metadata().fetchVariables().size() == expectedVariables.size(),
        "a stream with fetch variables %s".formatted(Arrays.toString(variables)),
        stream ->
            " but actual variables is %s"
                .formatted(
                    stream.metadata().fetchVariables().stream()
                        .map(BufferUtil::bufferAsString)
                        .toList()));
  }

  private String descriptionOrDefault(final String description, final Object... args) {
    final var userDescription = descriptionText();
    return userDescription.isBlank() ? description.formatted(args) : userDescription;
  }
}
