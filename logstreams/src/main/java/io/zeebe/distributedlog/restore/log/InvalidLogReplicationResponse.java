/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
