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
