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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_IMPORT_TYPE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_LOCATION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_NAMESPACE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_IMPORT;

import io.zeebe.model.bpmn.instance.Import;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN import element
 *
 * @author Daniel Meyer
 * @author Sebastian Menski
 */
public class ImportImpl extends BpmnModelElementInstanceImpl implements Import {

  protected static Attribute<String> namespaceAttribute;
  protected static Attribute<String> locationAttribute;
  protected static Attribute<String> importTypeAttribute;

  public ImportImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder bpmnModelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        bpmnModelBuilder
            .defineType(Import.class, BPMN_ELEMENT_IMPORT)
            .namespaceUri(BPMN20_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<Import>() {
                  @Override
                  public Import newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new ImportImpl(instanceContext);
                  }
                });

    namespaceAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAMESPACE).required().build();

    locationAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_LOCATION).required().build();

    importTypeAttribute =
        typeBuilder.stringAttribute(BPMN_ATTRIBUTE_IMPORT_TYPE).required().build();

    typeBuilder.build();
  }

  @Override
  public String getNamespace() {
    return namespaceAttribute.getValue(this);
  }

  @Override
  public void setNamespace(final String namespace) {
    namespaceAttribute.setValue(this, namespace);
  }

  @Override
  public String getLocation() {
    return locationAttribute.getValue(this);
  }

  @Override
  public void setLocation(final String location) {
    locationAttribute.setValue(this, location);
  }

  @Override
  public String getImportType() {
    return importTypeAttribute.getValue(this);
  }

  @Override
  public void setImportType(final String importType) {
    importTypeAttribute.setValue(this, importType);
  }
}
