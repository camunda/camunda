/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.model.dmn.impl.instance;

import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.LATEST_DMN_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_EXPORTER;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_EXPORTER_VERSION;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_EXPRESSION_LANGUAGE;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_NAMESPACE;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_TYPE_LANGUAGE;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_DEFINITIONS;

import java.util.Collection;

import org.camunda.bpm.model.dmn.instance.Artifact;
import org.camunda.bpm.model.dmn.instance.BusinessContextElement;
import org.camunda.bpm.model.dmn.instance.Definitions;
import org.camunda.bpm.model.dmn.instance.DrgElement;
import org.camunda.bpm.model.dmn.instance.ElementCollection;
import org.camunda.bpm.model.dmn.instance.Import;
import org.camunda.bpm.model.dmn.instance.ItemDefinition;
import org.camunda.bpm.model.dmn.instance.NamedElement;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

public class DefinitionsImpl extends NamedElementImpl implements Definitions {

  protected static Attribute<String> expressionLanguageAttribute;
  protected static Attribute<String> typeLanguageAttribute;
  protected static Attribute<String> namespaceAttribute;
  protected static Attribute<String> exporterAttribute;
  protected static Attribute<String> exporterVersionAttribute;

  protected static ChildElementCollection<Import> importCollection;
  protected static ChildElementCollection<ItemDefinition> itemDefinitionCollection;
  protected static ChildElementCollection<DrgElement> drgElementCollection;
  protected static ChildElementCollection<Artifact> artifactCollection;
  protected static ChildElementCollection<ElementCollection> elementCollectionCollection;
  protected static ChildElementCollection<BusinessContextElement> businessContextElementCollection;

  public DefinitionsImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public String getExpressionLanguage() {
    return expressionLanguageAttribute.getValue(this);
  }

  public void setExpressionLanguage(String expressionLanguage) {
    expressionLanguageAttribute.setValue(this, expressionLanguage);
  }

  public String getTypeLanguage() {
    return typeLanguageAttribute.getValue(this);
  }

  public void setTypeLanguage(String typeLanguage) {
    typeLanguageAttribute.setValue(this, typeLanguage);
  }

  public String getNamespace() {
    return namespaceAttribute.getValue(this);
  }

  public void setNamespace(String namespace) {
    namespaceAttribute.setValue(this, namespace);
  }

  public String getExporter() {
    return exporterAttribute.getValue(this);
  }

  public void setExporter(String exporter) {
    exporterAttribute.setValue(this, exporter);
  }

  public String getExporterVersion() {
    return exporterVersionAttribute.getValue(this);
  }

  public void setExporterVersion(String exporterVersion) {
    exporterVersionAttribute.setValue(this, exporterVersion);
  }

  public Collection<Import> getImports() {
    return importCollection.get(this);
  }

  public Collection<ItemDefinition> getItemDefinitions() {
    return itemDefinitionCollection.get(this);
  }

  public Collection<DrgElement> getDrgElements() {
    return drgElementCollection.get(this);
  }

  public Collection<Artifact> getArtifacts() {
    return artifactCollection.get(this);
  }

  public Collection<ElementCollection> getElementCollections() {
    return elementCollectionCollection.get(this);
  }

  public Collection<BusinessContextElement> getBusinessContextElements() {
    return businessContextElementCollection.get(this);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(Definitions.class, DMN_ELEMENT_DEFINITIONS)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(NamedElement.class)
      .instanceProvider(new ModelElementTypeBuilder.ModelTypeInstanceProvider<Definitions>() {
        public Definitions newInstance(ModelTypeInstanceContext instanceContext) {
          return new DefinitionsImpl(instanceContext);
        }
      });

    expressionLanguageAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_EXPRESSION_LANGUAGE)
      .defaultValue("http://www.omg.org/spec/FEEL/20140401")
      .build();

    typeLanguageAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_TYPE_LANGUAGE)
      .defaultValue("http://www.omg.org/spec/FEEL/20140401")
      .build();

    namespaceAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_NAMESPACE)
      .required()
      .build();

    exporterAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_EXPORTER)
      .build();

    exporterVersionAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_EXPORTER_VERSION)
      .build();

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    importCollection = sequenceBuilder.elementCollection(Import.class)
      .build();

    itemDefinitionCollection = sequenceBuilder.elementCollection(ItemDefinition.class)
      .build();

    drgElementCollection = sequenceBuilder.elementCollection(DrgElement.class)
      .build();

    artifactCollection = sequenceBuilder.elementCollection(Artifact.class)
      .build();

    elementCollectionCollection = sequenceBuilder.elementCollection(ElementCollection.class)
      .build();

    businessContextElementCollection = sequenceBuilder.elementCollection(BusinessContextElement.class)
      .build();

    typeBuilder.build();
  }

}
