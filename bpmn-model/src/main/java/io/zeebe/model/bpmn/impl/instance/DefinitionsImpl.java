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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_EXPORTER;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_EXPORTER_VERSION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_EXPRESSION_LANGUAGE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ID;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_NAME;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_TARGET_NAMESPACE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_TYPE_LANGUAGE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_DEFINITIONS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.XML_SCHEMA_NS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.XPATH_NS;

import io.zeebe.model.bpmn.instance.Definitions;
import io.zeebe.model.bpmn.instance.Extension;
import io.zeebe.model.bpmn.instance.Import;
import io.zeebe.model.bpmn.instance.Relationship;
import io.zeebe.model.bpmn.instance.RootElement;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnDiagram;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN definitions element
 *
 * @author Daniel Meyer
 * @author Sebastian Menski
 */
public class DefinitionsImpl extends BpmnModelElementInstanceImpl implements Definitions {

  protected static Attribute<String> idAttribute;
  protected static Attribute<String> nameAttribute;
  protected static Attribute<String> targetNamespaceAttribute;
  protected static Attribute<String> expressionLanguageAttribute;
  protected static Attribute<String> typeLanguageAttribute;
  protected static Attribute<String> exporterAttribute;
  protected static Attribute<String> exporterVersionAttribute;

  protected static ChildElementCollection<Import> importCollection;
  protected static ChildElementCollection<Extension> extensionCollection;
  protected static ChildElementCollection<RootElement> rootElementCollection;
  protected static ChildElementCollection<BpmnDiagram> bpmnDiagramCollection;
  protected static ChildElementCollection<Relationship> relationshipCollection;

  public DefinitionsImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder bpmnModelBuilder) {

    final ModelElementTypeBuilder typeBuilder =
        bpmnModelBuilder
            .defineType(Definitions.class, BPMN_ELEMENT_DEFINITIONS)
            .namespaceUri(BPMN20_NS)
            .instanceProvider(
                new ModelElementTypeBuilder.ModelTypeInstanceProvider<Definitions>() {
                  @Override
                  public Definitions newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new DefinitionsImpl(instanceContext);
                  }
                });

    idAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_ID).idAttribute().build();

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    targetNamespaceAttribute =
        typeBuilder.stringAttribute(BPMN_ATTRIBUTE_TARGET_NAMESPACE).required().build();

    expressionLanguageAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_EXPRESSION_LANGUAGE)
            .defaultValue(XPATH_NS)
            .build();

    typeLanguageAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_TYPE_LANGUAGE)
            .defaultValue(XML_SCHEMA_NS)
            .build();

    exporterAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_EXPORTER).build();

    exporterVersionAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_EXPORTER_VERSION).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    importCollection = sequenceBuilder.elementCollection(Import.class).build();

    extensionCollection = sequenceBuilder.elementCollection(Extension.class).build();

    rootElementCollection = sequenceBuilder.elementCollection(RootElement.class).build();

    bpmnDiagramCollection = sequenceBuilder.elementCollection(BpmnDiagram.class).build();

    relationshipCollection = sequenceBuilder.elementCollection(Relationship.class).build();

    typeBuilder.build();
  }

  @Override
  public String getId() {
    return idAttribute.getValue(this);
  }

  @Override
  public void setId(final String id) {
    idAttribute.setValue(this, id);
  }

  @Override
  public String getName() {
    return nameAttribute.getValue(this);
  }

  @Override
  public void setName(final String name) {
    nameAttribute.setValue(this, name);
  }

  @Override
  public String getTargetNamespace() {
    return targetNamespaceAttribute.getValue(this);
  }

  @Override
  public void setTargetNamespace(final String namespace) {
    targetNamespaceAttribute.setValue(this, namespace);
  }

  @Override
  public String getExpressionLanguage() {
    return expressionLanguageAttribute.getValue(this);
  }

  @Override
  public void setExpressionLanguage(final String expressionLanguage) {
    expressionLanguageAttribute.setValue(this, expressionLanguage);
  }

  @Override
  public String getTypeLanguage() {
    return typeLanguageAttribute.getValue(this);
  }

  @Override
  public void setTypeLanguage(final String typeLanguage) {
    typeLanguageAttribute.setValue(this, typeLanguage);
  }

  @Override
  public String getExporter() {
    return exporterAttribute.getValue(this);
  }

  @Override
  public void setExporter(final String exporter) {
    exporterAttribute.setValue(this, exporter);
  }

  @Override
  public String getExporterVersion() {
    return exporterVersionAttribute.getValue(this);
  }

  @Override
  public void setExporterVersion(final String exporterVersion) {
    exporterVersionAttribute.setValue(this, exporterVersion);
  }

  @Override
  public Collection<Import> getImports() {
    return importCollection.get(this);
  }

  @Override
  public Collection<Extension> getExtensions() {
    return extensionCollection.get(this);
  }

  @Override
  public Collection<RootElement> getRootElements() {
    return rootElementCollection.get(this);
  }

  @Override
  public Collection<BpmnDiagram> getBpmDiagrams() {
    return bpmnDiagramCollection.get(this);
  }

  @Override
  public Collection<Relationship> getRelationships() {
    return relationshipCollection.get(this);
  }
}
