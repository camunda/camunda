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
package io.zeebe.gateway.impl.broker.response;

import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class BrokerRejection {

  private final Intent intent;
  private final long key;
  private final RejectionType type;
  private final String reason;
  private final String message;

  public BrokerRejection(Intent intent, long key, RejectionType type, DirectBuffer reason) {
    this(intent, key, type, BufferUtil.bufferAsString(reason));
  }

  public BrokerRejection(Intent intent, long key, RejectionType type, String reason) {
    this.intent = intent;
    this.key = key;
    this.type = type;
    this.reason = reason;
    this.message = buildRejectionMessage();
  }

  public RejectionType getType() {
    return type;
  }

  public String getReason() {
    return reason;
  }

  public String getMessage() {
    return message;
  }

  private String buildRejectionMessage() {
    final StringBuilder sb = new StringBuilder();
    sb.append("Command (");
    sb.append(intent);
    sb.append(") ");

    if (key >= 0) {
      sb.append("for event with key ");
      sb.append(key);
      sb.append(" ");
    }

    sb.append("was rejected. ");
    sb.append(describeRejectionType());
    sb.append(" ");
    sb.append(reason);

    return sb.toString();
  }

  private String describeRejectionType() {
    switch (type) {
      case BAD_VALUE:
        return "It has an invalid value.";
      case NOT_APPLICABLE:
        return "It is not applicable in the current state.";
      case PROCESSING_ERROR:
        return "The broker could not process it for internal reasons.";
      default:
        // Nothing
        return "";
    }
  }

  @Override
  public String toString() {
    return message;
  }
}
