/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log;

import io.atomix.cluster.MemberId;

public class InvalidLogReplicationResponse extends RuntimeException {
  private static final long serialVersionUID = -7183625363283068148L;

  private final MemberId server;
  private final LogReplicationRequest request;
  private final LogReplicationResponse response;

  public InvalidLogReplicationResponse(
      MemberId server, LogReplicationRequest request, LogReplicationResponse response) {
    super(
        String.format(
            "Request %s to log replication server %s returned an invalid response %s",
            request, server, response));
    this.server = server;
    this.request = request;
    this.response = response;
  }

  public MemberId getServer() {
    return server;
  }

  public LogReplicationRequest getRequest() {
    return request;
  }

  public LogReplicationResponse getResponse() {
    return response;
  }
}
