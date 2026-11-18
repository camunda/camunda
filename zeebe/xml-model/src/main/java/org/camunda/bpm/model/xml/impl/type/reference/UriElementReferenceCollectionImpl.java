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
package org.camunda.bpm.model.xml.impl.type.reference;

import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;

public class UriElementReferenceCollectionImpl<Target extends ModelElementInstance, Source extends ModelElementInstance> extends ElementReferenceCollectionImpl<Target, Source> {

  public UriElementReferenceCollectionImpl(ChildElementCollection<Source> referenceSourceCollection) {
    super(referenceSourceCollection);
  }

  @Override
  public String getReferenceIdentifier(ModelElementInstance referenceSourceElement) {
    // TODO: implement something more robust (CAM-4028)
    String identifier = referenceSourceElement.getAttributeValue("href");
    if (identifier != null) {
      String[] parts = identifier.split("#");
      if (parts.length > 1) {
        return parts[parts.length - 1];
      }
      else {
        return parts[0];
      }
    }
    else {
      return null;
    }
  }

  @Override
  protected void setReferenceIdentifier(ModelElementInstance referenceSourceElement, String referenceIdentifier) {
    // TODO: implement something more robust (CAM-4028)
    referenceSourceElement.setAttributeValue("href", "#" + referenceIdentifier);
  }

}
