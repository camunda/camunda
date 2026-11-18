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
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_LOCATION_URI;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_KNOWLEDGE_SOURCE;

import java.util.Collection;

import org.camunda.bpm.model.dmn.instance.AuthorityRequirement;
import org.camunda.bpm.model.dmn.instance.DrgElement;
import org.camunda.bpm.model.dmn.instance.KnowledgeSource;
import org.camunda.bpm.model.dmn.instance.OrganizationUnit;
import org.camunda.bpm.model.dmn.instance.OwnerReference;
import org.camunda.bpm.model.dmn.instance.Type;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReference;

public class KnowledgeSourceImpl extends DrgElementImpl implements KnowledgeSource {

  protected static Attribute<String> locationUriAttribute;

  protected static ChildElementCollection<AuthorityRequirement> authorityRequirementCollection;
  protected static ChildElement<Type> typeChild;
  protected static ElementReference<OrganizationUnit, OwnerReference> ownerRef;

  public KnowledgeSourceImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public String getLocationUri() {
    return locationUriAttribute.getValue(this);
  }

  public void setLocationUri(String locationUri) {
    locationUriAttribute.setValue(this, locationUri);
  }

  public Collection<AuthorityRequirement> getAuthorityRequirement() {
    return authorityRequirementCollection.get(this);
  }

  public Type getType() {
    return typeChild.getChild(this);
  }

  public void setType(Type type) {
    typeChild.setChild(this, type);
  }

  public OrganizationUnit getOwner() {
    return ownerRef.getReferenceTargetElement(this);
  }

  public void setOwner(OrganizationUnit owner) {
    ownerRef.setReferenceTargetElement(this, owner);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(KnowledgeSource.class, DMN_ELEMENT_KNOWLEDGE_SOURCE)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(DrgElement.class)
      .instanceProvider(new ModelTypeInstanceProvider<KnowledgeSource>() {
        public KnowledgeSource newInstance(ModelTypeInstanceContext instanceContext) {
          return new KnowledgeSourceImpl(instanceContext);
        }
      });

    locationUriAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_LOCATION_URI)
      .build();

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    authorityRequirementCollection = sequenceBuilder.elementCollection(AuthorityRequirement.class)
      .build();

    typeChild = sequenceBuilder.element(Type.class)
      .build();

    ownerRef = sequenceBuilder.element(OwnerReference.class)
      .uriElementReference(OrganizationUnit.class)
      .build();

    typeBuilder.build();
  }

}
