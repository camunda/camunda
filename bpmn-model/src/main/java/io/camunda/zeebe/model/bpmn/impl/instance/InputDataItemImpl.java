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

package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_INPUT_DATA_ITEM;

import io.zeebe.model.bpmn.instance.DataInput;
import io.zeebe.model.bpmn.instance.InputDataItem;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;

/**
 * The BPMN 2.0 inputDataItem from the tMultiInstanceLoopCharacteristics type
 *
 * @author Filip Hrisafov
 */
public class InputDataItemImpl extends DataInputImpl implements InputDataItem {

  public InputDataItemImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(InputDataItem.class, BPMN_ELEMENT_INPUT_DATA_ITEM)
            .namespaceUri(BPMN20_NS)
            .extendsType(DataInput.class)
            .instanceProvider(
                new ModelElementTypeBuilder.ModelTypeInstanceProvider<InputDataItem>() {
                  @Override
                  public InputDataItem newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new InputDataItemImpl(instanceContext);
                  }
                });

    typeBuilder.build();
  }
}
