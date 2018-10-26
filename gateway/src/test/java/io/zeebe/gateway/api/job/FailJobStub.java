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

import io.zeebe.gateway.api.util.StubbedGateway;
import io.zeebe.gateway.api.util.StubbedGateway.RequestStub;
import io.zeebe.gateway.impl.broker.request.BrokerFailJobRequest;
import io.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.zeebe.protocol.impl.record.value.job.JobRecord;

public class FailJobStub extends JobRequestStub
    implements RequestStub<BrokerFailJobRequest, BrokerResponse<JobRecord>> {

  @Override
  public BrokerResponse<JobRecord> handle(BrokerFailJobRequest request) throws Exception {
    final JobRecord responseValue = buildDefaultValue();

    responseValue.setRetries(request.getRequestWriter().getRetries());
    return new BrokerResponse<>(responseValue, 0, request.getKey());
  }

  @Override
  public void registerWith(StubbedGateway gateway) {
    gateway.registerHandler(BrokerFailJobRequest.class, this);
  }
}
