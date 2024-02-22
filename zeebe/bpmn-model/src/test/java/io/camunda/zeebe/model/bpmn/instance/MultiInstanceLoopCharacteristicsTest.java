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

package io.camunda.zeebe.model.bpmn.instance;

import io.camunda.zeebe.model.bpmn.MultiInstanceFlowCondition;
import io.camunda.zeebe.model.bpmn.impl.instance.LoopDataInputRef;
import io.camunda.zeebe.model.bpmn.impl.instance.LoopDataOutputRef;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Filip Hrisafov
 */
public class MultiInstanceLoopCharacteristicsTest extends BpmnModelElementInstanceTest {

  @Override
  public TypeAssumption getTypeAssumption() {
    return new TypeAssumption(LoopCharacteristics.class, false);
  }

  @Override
  public Collection<ChildElementAssumption> getChildElementAssumptions() {
    return Arrays.asList(
        new ChildElementAssumption(LoopCardinality.class, 0, 1),
        new ChildElementAssumption(LoopDataInputRef.class, 0, 1),
        new ChildElementAssumption(LoopDataOutputRef.class, 0, 1),
        new ChildElementAssumption(OutputDataItem.class, 0, 1),
        new ChildElementAssumption(InputDataItem.class, 0, 1),
        new ChildElementAssumption(ComplexBehaviorDefinition.class),
        new ChildElementAssumption(CompletionCondition.class, 0, 1));
  }

  @Override
  public Collection<AttributeAssumption> getAttributesAssumptions() {
    return Arrays.asList(
        new AttributeAssumption("isSequential", false, false, false),
        new AttributeAssumption("behavior", false, false, MultiInstanceFlowCondition.All),
        new AttributeAssumption("oneBehaviorEventRef"),
        new AttributeAssumption("noneBehaviorEventRef"));
  }
}
