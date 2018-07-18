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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_RESOURCE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_SCRIPT_FORMAT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ELEMENT_SCRIPT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.camunda.CamundaScript;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN script camunda extension element
 *
 * @author Sebastian Menski
 */
public class CamundaScriptImpl extends BpmnModelElementInstanceImpl implements CamundaScript {

  protected static Attribute<String> camundaScriptFormatAttribute;
  protected static Attribute<String> camundaResourceAttribute;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(CamundaScript.class, CAMUNDA_ELEMENT_SCRIPT)
            .namespaceUri(CAMUNDA_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<CamundaScript>() {
                  @Override
                  public CamundaScript newInstance(ModelTypeInstanceContext instanceContext) {
                    return new CamundaScriptImpl(instanceContext);
                  }
                });

    camundaScriptFormatAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_SCRIPT_FORMAT).required().build();

    camundaResourceAttribute = typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_RESOURCE).build();

    typeBuilder.build();
  }

  public CamundaScriptImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getCamundaScriptFormat() {
    return camundaScriptFormatAttribute.getValue(this);
  }

  @Override
  public void setCamundaScriptFormat(String camundaScriptFormat) {
    camundaScriptFormatAttribute.setValue(this, camundaScriptFormat);
  }

  @Override
  public String getCamundaResource() {
    return camundaResourceAttribute.getValue(this);
  }

  @Override
  public void setCamundaResource(String camundaResource) {
    camundaResourceAttribute.setValue(this, camundaResource);
  }
}
