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
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_TYPE_REF;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_INFORMATION_ITEM;

import org.camunda.bpm.model.dmn.instance.InformationItem;
import org.camunda.bpm.model.dmn.instance.NamedElement;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class InformationItemImpl extends NamedElementImpl implements InformationItem {

  protected static Attribute<String> typeRefAttribute;

  public InformationItemImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public String getTypeRef() {
    return typeRefAttribute.getValue(this);
  }

  public void setTypeRef(String typeRef) {
    typeRefAttribute.setValue(this, typeRef);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(InformationItem.class, DMN_ELEMENT_INFORMATION_ITEM)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(NamedElement.class)
      .instanceProvider(new ModelTypeInstanceProvider<InformationItem>() {
        public InformationItem newInstance(ModelTypeInstanceContext instanceContext) {
          return new InformationItemImpl(instanceContext);
        }
      });

    typeRefAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_TYPE_REF)
      .build();

    typeBuilder.build();
  }

}
