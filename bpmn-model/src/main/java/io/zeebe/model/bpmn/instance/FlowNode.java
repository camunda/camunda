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

package io.zeebe.model.bpmn.instance;

import io.zeebe.model.bpmn.Query;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.util.Collection;

/**
 * The BPMN flowNode element
 *
 * @author Sebastian Menski
 */
public interface FlowNode extends FlowElement {

  @Override
  @SuppressWarnings("rawtypes")
  AbstractFlowNodeBuilder builder();

  Collection<SequenceFlow> getIncoming();

  Collection<SequenceFlow> getOutgoing();

  Query<FlowNode> getPreviousNodes();

  Query<FlowNode> getSucceedingNodes();
}
