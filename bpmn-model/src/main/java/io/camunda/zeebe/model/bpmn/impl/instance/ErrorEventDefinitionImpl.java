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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ERROR_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_ERROR_EVENT_DEFINITION;

import io.zeebe.model.bpmn.instance.Error;
import io.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.zeebe.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN errorEventDefinition element
 *
 * @author Sebastian Menski
 */
public class ErrorEventDefinitionImpl extends EventDefinitionImpl implements ErrorEventDefinition {

  protected static AttributeReference<Error> errorRefAttribute;

  public ErrorEventDefinitionImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ErrorEventDefinition.class, BPMN_ELEMENT_ERROR_EVENT_DEFINITION)
            .namespaceUri(BPMN20_NS)
            .extendsType(EventDefinition.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ErrorEventDefinition>() {
                  @Override
                  public ErrorEventDefinition newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new ErrorEventDefinitionImpl(instanceContext);
                  }
                });

    errorRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_ERROR_REF)
            .qNameAttributeReference(Error.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public Error getError() {
    return errorRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setError(final Error error) {
    errorRefAttribute.setReferenceTargetElement(this, error);
  }
}
