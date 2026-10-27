/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.MessagingException;
import io.atomix.cluster.messaging.MessagingException.ConnectionClosed;
import io.atomix.cluster.messaging.MessagingException.NoRemoteHandler;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.atomix.cluster.messaging.MessagingException.RemoteHandlerFailure;
import io.atomix.raft.utils.VoteQuorum.VoteErrorStatus;
import java.net.ConnectException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

final class VoteErrorStatusTest {

  @Nested
  final class TransportFailures {

    static Stream<Arguments> transportFailures() {
      return Stream.of(
          Arguments.of(
              Named.of("member not in membership view", new NoSuchMemberException("member 2")),
              VoteErrorStatus.NO_SUCH_MEMBER),
          Arguments.of(
              Named.of("connection refused", new ConnectException("connection refused")),
              VoteErrorStatus.NO_SUCH_MEMBER),
          Arguments.of(
              Named.of("partition handler not registered", new NoRemoteHandler("vote-subject")),
              VoteErrorStatus.NO_SUCH_MEMBER),
          Arguments.of(
              Named.of(
                  "no response within the request timeout",
                  new TimeoutException("Request PollRequest to 0.0.0.0:26502 timed out in PT2.5S")),
              VoteErrorStatus.MEMBER_TIMED_OUT));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transportFailures")
    void shouldClassifyBareTransportFailure(final Throwable error, final VoteErrorStatus expected) {
      // given / when
      final var status = VoteErrorStatus.of(error);

      // then
      assertThat(status).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("transportFailures")
    void shouldClassifyTransportFailureWrappedByCompletionStage(
        final Throwable error, final VoteErrorStatus expected) {
      // given
      final var wrapped = new CompletionException(error);

      // when
      final var status = VoteErrorStatus.of(wrapped);

      // then
      assertThat(status).isEqualTo(expected);
    }
  }

  @Nested
  final class UnclassifiedFailures {

    static Stream<Arguments> unclassifiedFailures() {
      return Stream.of(
          Arguments.of(
              Named.of(
                  "channel closed before the response arrived",
                  new ConnectionClosed("channel was closed unexpectedly"))),
          Arguments.of(
              Named.of("remote handler threw", new RemoteHandlerFailure("handler failed"))),
          Arguments.of(Named.of("malformed message", new MessagingException.ProtocolException())),
          Arguments.of(Named.of("unrelated failure", new IllegalStateException("boom"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unclassifiedFailures")
    void shouldFallBackToUnknownForUnclassifiedFailure(final Throwable error) {
      // given / when
      final var status = VoteErrorStatus.of(error);

      // then
      assertThat(status).isEqualTo(VoteErrorStatus.UNKNOWN);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unclassifiedFailures")
    void shouldFallBackToUnknownWhenUnclassifiedFailureIsWrapped(final Throwable error) {
      // given
      final var wrapped = new CompletionException(error);

      // when
      final var status = VoteErrorStatus.of(wrapped);

      // then
      assertThat(status).isEqualTo(VoteErrorStatus.UNKNOWN);
    }

    @Test
    void shouldFallBackToUnknownForAnExecutionExceptionAroundAKnownFailure() {
      // given
      final var error = new ExecutionException(new NoSuchMemberException("member 2"));

      // when
      final var status = VoteErrorStatus.of(error);

      // then
      assertThat(status).isEqualTo(VoteErrorStatus.UNKNOWN);
    }

    @Test
    void shouldFallBackToUnknownForAWrapperWithoutACause() {
      // given
      final var error = new CompletionException(null);

      // when
      final var status = VoteErrorStatus.of(error);

      // then
      assertThat(status).isEqualTo(VoteErrorStatus.UNKNOWN);
    }
  }

  @Nested
  final class LogLevels {

    static Stream<Arguments> unreachableMemberFailures() {
      return Stream.of(
          Arguments.of(
              Named.of("member not in membership view", new NoSuchMemberException("member 2"))),
          Arguments.of(Named.of("connection refused", new ConnectException("connection refused"))),
          Arguments.of(
              Named.of("partition handler not registered", new NoRemoteHandler("vote-subject"))));
    }

    /**
     * Every failure classified as an unreachable member has to be demoted, not only the one
     * exception type that names it.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("unreachableMemberFailures")
    void shouldDemoteEveryUnreachableMemberFailure(final Throwable error) {
      // given
      final var wrapped = new CompletionException(error);

      // when
      final var level = VoteErrorStatus.of(wrapped).logLevel();

      // then
      assertThat(level).isEqualTo(Level.TRACE);
    }

    @Test
    void shouldKeepReportingOtherFailuresAtWarn() {
      // given
      final var error = new CompletionException(new IllegalStateException("boom"));

      // when
      final var level = VoteErrorStatus.of(error).logLevel();

      // then
      assertThat(level).isEqualTo(Level.WARN);
    }
  }
}
