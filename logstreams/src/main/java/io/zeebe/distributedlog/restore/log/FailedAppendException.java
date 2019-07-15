/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log;

import io.atomix.cluster.MemberId;

public class FailedAppendException extends RuntimeException {
  private static final long serialVersionUID = -8427379674890259750L;
  private static final String MESSAGE_FORMAT =
      "Expected to append events '%d' to '%d' replicated from '%s', but appender failed with result '%d'";

  private final MemberId server;
  private final long from;
  private final long to;
  private final long appendResult;

  public FailedAppendException(MemberId server, long from, long to, long appendResult) {
    super(String.format(MESSAGE_FORMAT, from, to, server.toString(), appendResult));
    this.server = server;
    this.from = from;
    this.to = to;
    this.appendResult = appendResult;
  }

  public MemberId getServer() {
    return server;
  }

  public long getAppendResult() {
    return appendResult;
  }

  public long getFrom() {
    return from;
  }

  public long getTo() {
    return to;
  }
}
