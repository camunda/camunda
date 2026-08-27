/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.utils;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingException.NoRemoteHandler;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.camunda.zeebe.util.concurrency.FuturesUtil;
import java.net.ConnectException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.event.Level;

public interface VoteQuorum {

  void succeed(MemberId member);

  void fail(MemberId member, VoteErrorStatus status);

  void cancel();

  enum VoteErrorStatus {
    NO_SUCH_MEMBER,
    MEMBER_UNREACHABLE,
    MEMBER_NOT_READY,
    MEMBER_TIMED_OUT,
    REJECTED,
    INVALID_TERM,
    UNKNOWN;

    /**
     * True when the member delivered a response and the failure is a regular protocol outcome (a
     * denied vote or a term conflict) rather than a failure to reach the member.
     */
    public boolean isProtocolOutcome() {
      return this == REJECTED || this == INVALID_TERM;
    }

    public static VoteErrorStatus of(final Throwable error) {
      return switch (FuturesUtil.unwrapCompletionException(error)) {
        case final NoSuchMemberException noSuchMemberException -> NO_SUCH_MEMBER;
        case final ConnectException connectException -> MEMBER_UNREACHABLE;
        case final NoRemoteHandler noRemoteHandler -> MEMBER_NOT_READY;
        case final TimeoutException timeoutException -> MEMBER_TIMED_OUT;
        case final CompletionException wrapper when wrapper.getCause() != null ->
            of(wrapper.getCause());
        default -> UNKNOWN;
      };
    }

    /**
     * The level to report a single failed request at. Every failure that means the member could not
     * be reached is demoted, so a booting or removed member does not flood the log once per
     * election round; the quorum still reports the collected states once when the election fails.
     */
    public Level logLevel() {
      return switch (this) {
        case NO_SUCH_MEMBER, MEMBER_UNREACHABLE, MEMBER_NOT_READY -> Level.TRACE;
        default -> Level.WARN;
      };
    }
  }
}
