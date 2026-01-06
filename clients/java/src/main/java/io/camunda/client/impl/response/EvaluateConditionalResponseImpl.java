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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.EvaluateConditionalResponse;
import io.camunda.client.api.response.ProcessInstanceReference;
import io.camunda.client.protocol.rest.EvaluateConditionalResult;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import java.util.List;
import java.util.stream.Collectors;

public final class EvaluateConditionalResponseImpl implements EvaluateConditionalResponse {

  private final List<ProcessInstanceReference> processInstances;

  public EvaluateConditionalResponseImpl(
      final GatewayOuterClass.EvaluateConditionalResponse grpcResponse) {
    processInstances =
        grpcResponse.getProcessInstancesList().stream()
            .map(ProcessInstanceReferenceImpl::new)
            .collect(Collectors.toList());
  }

  public EvaluateConditionalResponseImpl(final EvaluateConditionalResult restResponse) {
    final List<io.camunda.client.protocol.rest.ProcessInstanceReference> restInstances =
        restResponse.getProcessInstances();

    processInstances =
        restInstances.stream().map(ProcessInstanceReferenceImpl::new).collect(Collectors.toList());
  }

  @Override
  public List<ProcessInstanceReference> getProcessInstances() {
    return processInstances;
  }
}
