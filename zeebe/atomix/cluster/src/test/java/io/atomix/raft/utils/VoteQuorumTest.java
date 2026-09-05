/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.atomix.cluster.MemberId;
import io.atomix.raft.utils.VoteQuorum.VoteErrorStatus;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class VoteQuorumTest {
  @Nested
  final class SimpleVoteQuorumTest {

    @Test
    void shouldWaitWith1OutOf3() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new SimpleVoteQuorum(
              callback, Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.succeed(new MemberId("1"));

      // then
      verifyNoInteractions(callback);
    }

    @Test
    void shouldSucceedWith2OutOf3() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new SimpleVoteQuorum(
              callback, Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.succeed(new MemberId("1"));
      quorum.succeed(new MemberId("2"));

      // then
      verify(callback, only()).accept(true);
    }

    @Test
    void shouldIgnoreDuplicates() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new SimpleVoteQuorum(
              callback, Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.succeed(new MemberId("1"));
      quorum.succeed(new MemberId("1"));

      // then
      verifyNoInteractions(callback);
    }

    @Test
    void shouldIgnoreUnknown() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new SimpleVoteQuorum(
              callback, Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.succeed(new MemberId("5"));
      quorum.succeed(new MemberId("6"));

      // then
      verifyNoInteractions(callback);
    }

    @Test
    void shouldOnlySucceedOnce() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new SimpleVoteQuorum(
              callback, Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.succeed(new MemberId("1"));
      quorum.succeed(new MemberId("2"));
      quorum.succeed(new MemberId("3"));

      // then
      verify(callback, only()).accept(true);
    }

    @Test
    void shouldFail() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new SimpleVoteQuorum(
              callback, Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.fail(new MemberId("1"), VoteErrorStatus.UNKNOWN);
      quorum.fail(new MemberId("2"), VoteErrorStatus.UNKNOWN);

      // then
      verify(callback, only()).accept(false);
    }

    @Test
    void shouldNotRecoverFromFail() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new SimpleVoteQuorum(
              callback, Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.fail(new MemberId("1"), VoteErrorStatus.UNKNOWN);
      quorum.fail(new MemberId("2"), VoteErrorStatus.UNKNOWN);
      quorum.succeed(new MemberId("1"));
      quorum.succeed(new MemberId("2"));

      // then
      verify(callback, only()).accept(false);
    }
  }

  @Nested
  final class JointConsensus {
    @Test
    void shouldSucceedWithOverlappingMembers() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new JointConsensusVoteQuorum(
              callback,
              Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("4")),
              Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.succeed(new MemberId("1"));
      quorum.succeed(new MemberId("2"));

      // then
      verify(callback, only()).accept(true);
    }

    @Test
    void shouldSucceedWithDisjointMembers() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new JointConsensusVoteQuorum(
              callback,
              Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")),
              Set.of(MemberId.from("4"), MemberId.from("5"), MemberId.from("6")));

      // when
      quorum.succeed(new MemberId("1"));
      quorum.succeed(new MemberId("2"));
      quorum.succeed(new MemberId("4"));
      quorum.succeed(new MemberId("5"));

      // then
      verify(callback, only()).accept(true);
    }

    @Test
    void shouldWaitWithQuorumOfOldMembers() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new JointConsensusVoteQuorum(
              callback,
              Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("4")),
              Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.succeed(new MemberId("1"));
      quorum.succeed(new MemberId("4"));

      // then
      verifyNoInteractions(callback);
    }

    @Test
    void shouldWaitWithQuorumOfNewMembers() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new JointConsensusVoteQuorum(
              callback,
              Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("4")),
              Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.succeed(new MemberId("1"));
      quorum.succeed(new MemberId("3"));

      // then
      verifyNoInteractions(callback);
    }

    @Test
    void shouldFailOnPartialFailure() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new JointConsensusVoteQuorum(
              callback,
              Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("4")),
              Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.fail(new MemberId("1"), VoteErrorStatus.UNKNOWN);
      quorum.fail(new MemberId("4"), VoteErrorStatus.UNKNOWN);

      // then
      verify(callback, only()).accept(false);
    }

    @Test
    void shouldNotRecoverFromFailure() {
      // given
      final Consumer<Boolean> callback = mock();

      final var quorum =
          new JointConsensusVoteQuorum(
              callback,
              Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("4")),
              Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

      // when
      quorum.fail(new MemberId("1"), VoteErrorStatus.UNKNOWN);
      quorum.fail(new MemberId("4"), VoteErrorStatus.UNKNOWN);
      quorum.succeed(new MemberId("1"));
      quorum.succeed(new MemberId("2"));
      quorum.succeed(new MemberId("3"));

      // then
      verify(callback, only()).accept(false);
    }
  }

  @Nested
  final class FailureReports {

    @Test
    void shouldWarnWithMemberStatesWhenQuorumFails() {
      // given
      final Consumer<Boolean> callback = mock();

      try (final var logs = LogCapturer.capturing(SimpleVoteQuorum.class, Level.DEBUG)) {
        final var quorum =
            new SimpleVoteQuorum(
                callback, Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

        // when
        quorum.fail(new MemberId("1"), VoteErrorStatus.NO_SUCH_MEMBER);
        quorum.fail(new MemberId("2"), VoteErrorStatus.MEMBER_TIMED_OUT);

        // then
        assertThat(logs.messages()).hasSize(1);
        assertThat(logs.messagesAt(Level.WARN))
            .singleElement()
            .satisfies(
                message ->
                    assertThat(message)
                        .contains("2/3")
                        .contains("1=NO_SUCH_MEMBER")
                        .contains("2=MEMBER_TIMED_OUT"));
      }
    }

    @Test
    void shouldReportDeniedVotesAtDebug() {
      // given
      final Consumer<Boolean> callback = mock();

      try (final var logs = LogCapturer.capturing(SimpleVoteQuorum.class, Level.DEBUG)) {
        final var quorum =
            new SimpleVoteQuorum(
                callback, Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

        // when
        quorum.fail(new MemberId("1"), VoteErrorStatus.REJECTED);
        quorum.fail(new MemberId("2"), VoteErrorStatus.INVALID_TERM);

        // then
        assertThat(logs.messages()).hasSize(1);
        assertThat(logs.messagesAt(Level.DEBUG)).hasSize(1);
      }
    }

    @Test
    void shouldReportCollectedFailuresAndPendingMembersWhenCancelled() {
      // given
      final Consumer<Boolean> callback = mock();

      try (final var logs = LogCapturer.capturing(SimpleVoteQuorum.class, Level.DEBUG)) {
        final var quorum =
            new SimpleVoteQuorum(
                callback, Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

        // when
        quorum.fail(new MemberId("1"), VoteErrorStatus.NO_SUCH_MEMBER);
        quorum.cancel();

        // then
        assertThat(logs.messages()).hasSize(1);
        assertThat(logs.messagesAt(Level.WARN))
            .singleElement()
            .satisfies(
                message ->
                    assertThat(message)
                        .contains("cancelled before completion")
                        .contains("1=NO_SUCH_MEMBER")
                        .contains("2")
                        .contains("3"));
      }
    }

    @Test
    void shouldStayQuietWhenCancelledWithoutFailures() {
      // given
      final Consumer<Boolean> callback = mock();

      try (final var logs = LogCapturer.capturing(SimpleVoteQuorum.class, Level.DEBUG)) {
        final var quorum =
            new SimpleVoteQuorum(
                callback, Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")));

        // when
        quorum.succeed(new MemberId("1"));
        quorum.cancel();

        // then
        assertThat(logs.messages()).isEmpty();
      }
    }

    @Test
    void shouldWarnOnceWhenJointConsensusFails() {
      // given
      final Consumer<Boolean> callback = mock();

      try (final var logs = LogCapturer.capturing(SimpleVoteQuorum.class, Level.DEBUG)) {
        final var quorum =
            new JointConsensusVoteQuorum(
                callback,
                Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")),
                Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("4")));

        // when
        quorum.fail(new MemberId("1"), VoteErrorStatus.NO_SUCH_MEMBER);
        quorum.fail(new MemberId("2"), VoteErrorStatus.NO_SUCH_MEMBER);

        // then
        assertThat(logs.messagesAt(Level.WARN))
            .singleElement()
            .satisfies(
                message -> assertThat(message).contains("Old configuration quorum failed with"));
      }
    }

    @Test
    void shouldLogAbandonedSiblingStatesAtDebugWhenJointConsensusFails() {
      // given
      final Consumer<Boolean> callback = mock();

      try (final var logs = LogCapturer.capturing(SimpleVoteQuorum.class, Level.DEBUG)) {
        final var quorum =
            new JointConsensusVoteQuorum(
                callback,
                Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")),
                Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("4")));

        // when
        quorum.fail(new MemberId("1"), VoteErrorStatus.NO_SUCH_MEMBER);
        quorum.fail(new MemberId("2"), VoteErrorStatus.NO_SUCH_MEMBER);

        // then
        assertThat(logs.messagesAt(Level.DEBUG))
            .anySatisfy(
                message ->
                    assertThat(message)
                        .contains("New configuration quorum cancelled before completion")
                        .contains("1=NO_SUCH_MEMBER"));
      }
    }

    @Test
    void shouldWarnPerConfigurationWhenAJointElectionIsCancelled() {
      // given
      final Consumer<Boolean> callback = mock();

      try (final var logs = LogCapturer.capturing(SimpleVoteQuorum.class, Level.DEBUG)) {
        final var quorum =
            new JointConsensusVoteQuorum(
                callback,
                Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("3")),
                Set.of(MemberId.from("1"), MemberId.from("2"), MemberId.from("4")));

        // when
        quorum.fail(new MemberId("3"), VoteErrorStatus.NO_SUCH_MEMBER);
        quorum.fail(new MemberId("4"), VoteErrorStatus.MEMBER_TIMED_OUT);
        quorum.cancel();

        // then
        assertThat(logs.messagesAt(Level.WARN))
            .hasSize(2)
            .anySatisfy(
                message ->
                    assertThat(message)
                        .contains("Old configuration quorum cancelled before completion")
                        .contains("3=NO_SUCH_MEMBER"))
            .anySatisfy(
                message ->
                    assertThat(message)
                        .contains("New configuration quorum cancelled before completion")
                        .contains("4=MEMBER_TIMED_OUT"));
      }
    }
  }
}
