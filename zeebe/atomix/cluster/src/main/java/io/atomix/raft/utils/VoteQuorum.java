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
import java.util.concurrent.TimeoutException;
import org.slf4j.event.Level;

public interface VoteQuorum {

  void succeed(MemberId member);

  void fail(MemberId member, VoteErrorStatus status);

  void cancel();

  enum VoteErrorStatus {
    NO_SUCH_MEMBER,
    MEMBER_TIMED_OUT,
    REJECTED,
    INVALID_TERM,
    UNKNOWN;

    public static VoteErrorStatus of(final Throwable error) {
      return switch (FuturesUtil.unwrapCompletionException(error)) {
        case final NoSuchMemberException noSuchMemberException -> NO_SUCH_MEMBER;
        case final ConnectException connectException -> NO_SUCH_MEMBER;
        case final NoRemoteHandler noRemoteHandler -> NO_SUCH_MEMBER;
        case final TimeoutException timeoutException -> MEMBER_TIMED_OUT;
        default -> UNKNOWN;
      };
    }

    public Level logLevel() {
      return this == NO_SUCH_MEMBER ? Level.TRACE : Level.WARN;
    }
  }
}
