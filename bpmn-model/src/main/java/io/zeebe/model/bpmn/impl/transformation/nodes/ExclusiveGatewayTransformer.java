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
package io.zeebe.model.bpmn.impl.transformation.nodes;

import io.zeebe.model.bpmn.impl.instance.ExclusiveGatewayImpl;
import io.zeebe.model.bpmn.impl.instance.SequenceFlowImpl;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import java.util.List;
import java.util.stream.Collectors;

public class ExclusiveGatewayTransformer {
  public void transform(List<ExclusiveGatewayImpl> exclusiveGateways) {
    for (int e = 0; e < exclusiveGateways.size(); e++) {
      final ExclusiveGatewayImpl exclusiveGateway = exclusiveGateways.get(e);

      final List<SequenceFlow> sequenceFlowsWithConditions =
          exclusiveGateway
              .getOutgoing()
              .stream()
              .filter(SequenceFlowImpl::hasCondition)
              .collect(Collectors.toList());

      exclusiveGateway.setOutgoingSequenceFlowsWithConditions(sequenceFlowsWithConditions);
    }
  }
}
