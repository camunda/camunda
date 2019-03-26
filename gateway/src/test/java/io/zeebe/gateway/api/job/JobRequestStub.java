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
package io.zeebe.gateway.api.job;

import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.test.util.MsgPackUtil;
import org.agrona.DirectBuffer;

public class JobRequestStub {

  public static final long KEY = 789;
  public static final long DEADLINE = 123;
  public static final String TYPE = "type";
  public static final String WORKER = "worker";
  public static final DirectBuffer VARIABLES = MsgPackUtil.asMsgPack("key", "val");
  public static final DirectBuffer CUSTOM_HEADERS = MsgPackUtil.asMsgPack("headerKey", "headerVal");
  public static final int RETRIES = 456;

  public long getKey() {
    return KEY;
  }

  public long getDeadline() {
    return DEADLINE;
  }

  public String getType() {
    return TYPE;
  }

  public String getWorker() {
    return WORKER;
  }

  public DirectBuffer getVariables() {
    return VARIABLES;
  }

  public DirectBuffer getCustomHeaders() {
    return CUSTOM_HEADERS;
  }

  protected JobRecord buildDefaultValue() {
    final JobRecord value = new JobRecord();
    value.setCustomHeaders(CUSTOM_HEADERS);
    value.setDeadline(DEADLINE);
    value.setVariables(VARIABLES);
    value.setRetries(RETRIES);
    value.setType(TYPE);
    value.setWorker(WORKER);

    return value;
  }
}
