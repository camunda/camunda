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

package io.zeebe.model.bpmn.impl.instance.camunda;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ELEMENT_FORM_DATA;

import io.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.camunda.CamundaFormData;
import io.zeebe.model.bpmn.instance.camunda.CamundaFormField;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN formData camunda extension element
 *
 * @author Sebastian Menski
 */
public class CamundaFormDataImpl extends BpmnModelElementInstanceImpl implements CamundaFormData {

  protected static ChildElementCollection<CamundaFormField> camundaFormFieldCollection;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(CamundaFormData.class, CAMUNDA_ELEMENT_FORM_DATA)
            .namespaceUri(BpmnModelConstants.CAMUNDA_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<CamundaFormData>() {
                  @Override
                  public CamundaFormData newInstance(ModelTypeInstanceContext instanceContext) {
                    return new CamundaFormDataImpl(instanceContext);
                  }
                });

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    camundaFormFieldCollection = sequenceBuilder.elementCollection(CamundaFormField.class).build();

    typeBuilder.build();
  }

  public CamundaFormDataImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public Collection<CamundaFormField> getCamundaFormFields() {
    return camundaFormFieldCollection.get(this);
  }
}
