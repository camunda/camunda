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
package io.zeebe.gateway.api;

import io.zeebe.gateway.api.StubbedGateway.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerUpdateJobRetriesRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.test.util.MsgPackUtil;
import org.agrona.DirectBuffer;

public class UpdateJobRetriesStub
    implements RequestStub<BrokerUpdateJobRetriesRequest, BrokerResponse<JobRecord>> {

  public static final long KEY = 789;
  public static final long DEADLINE = 123;
  public static final String TYPE = "type";
  public static final String WORKER = "worker";
  public static final DirectBuffer PAYLOAD = MsgPackUtil.asMsgPack("payloadKey", "payloadVal");
  public static final DirectBuffer CUSTOM_HEADERS = MsgPackUtil.asMsgPack("headerKey", "headerVal");

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerUpdateJobRetriesRequest.class, this);
  }

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

  public DirectBuffer getPayload() {
    return PAYLOAD;
  }

  public DirectBuffer getCustomHeaders() {
    return CUSTOM_HEADERS;
  }

  @Override
  public BrokerResponse<JobRecord> handle(BrokerUpdateJobRetriesRequest request) throws Exception {
    final JobRecord response = new JobRecord();
    response.setCustomHeaders(CUSTOM_HEADERS);
    response.setDeadline(DEADLINE);
    response.setPayload(PAYLOAD);
    response.setRetries(request.getRequestWriter().getRetries());
    response.setType(TYPE);
    response.setWorker(WORKER);

    return new BrokerResponse<>(response, 0, KEY);
  }
}
