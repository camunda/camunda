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
package io.zeebe.model.bpmn.instance.zeebe;

import io.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.zeebe.model.bpmn.instance.BpmnModelElementInstanceTest;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class ZeebeIoMappingTest extends BpmnModelElementInstanceTest {

  @Override
  public TypeAssumption getTypeAssumption() {
    return new TypeAssumption(BpmnModelConstants.ZEEBE_NS, false);
  }

  @Override
  public Collection<ChildElementAssumption> getChildElementAssumptions() {
    return Arrays.asList(
        new ChildElementAssumption(BpmnModelConstants.ZEEBE_NS, ZeebeInput.class),
        new ChildElementAssumption(BpmnModelConstants.ZEEBE_NS, ZeebeOutput.class));
  }

  @Override
  public Collection<AttributeAssumption> getAttributesAssumptions() {
    return Collections.emptyList();
  }
}
