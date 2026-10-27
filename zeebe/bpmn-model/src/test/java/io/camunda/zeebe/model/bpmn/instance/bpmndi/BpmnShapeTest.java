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

package io.camunda.zeebe.model.bpmn.instance.bpmndi;

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_NS;

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstanceTest;
import io.camunda.zeebe.model.bpmn.instance.di.LabeledShape;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author Sebastian Menski
 */
public class BpmnShapeTest extends BpmnModelElementInstanceTest {

  @Override
  public TypeAssumption getTypeAssumption() {
    return new TypeAssumption(BPMNDI_NS, LabeledShape.class, false);
  }

  @Override
  public Collection<ChildElementAssumption> getChildElementAssumptions() {
    return Arrays.asList(new ChildElementAssumption(BPMNDI_NS, BpmnLabel.class, 0, 1));
  }

  @Override
  public Collection<AttributeAssumption> getAttributesAssumptions() {
    return Arrays.asList(
        new AttributeAssumption("bpmnElement"),
        new AttributeAssumption("isHorizontal"),
        new AttributeAssumption("isExpanded"),
        new AttributeAssumption("isMarkerVisible"),
        new AttributeAssumption("isMessageVisible"),
        new AttributeAssumption("participantBandKind"),
        new AttributeAssumption("choreographyActivityShape"));
  }
}
