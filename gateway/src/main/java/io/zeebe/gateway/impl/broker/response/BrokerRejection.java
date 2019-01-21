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

  public BrokerRejection(Intent intent, long key, RejectionType type, DirectBuffer reason) {
    this(intent, key, type, BufferUtil.bufferAsString(reason));
  }

  public BrokerRejection(Intent intent, long key, RejectionType type, String reason) {
    this.intent = intent;
    this.key = key;
    this.type = type;
    this.reason = reason;
  }

  public RejectionType getType() {
    return type;
  }

  public String getReason() {
    return reason;
  }

  public Intent getIntent() {
    return intent;
  }

  public long getKey() {
    return key;
  }
}
