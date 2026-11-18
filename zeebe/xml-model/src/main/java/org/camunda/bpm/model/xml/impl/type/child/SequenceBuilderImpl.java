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
package org.camunda.bpm.model.xml.impl.type.child;

import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.impl.ModelBuildOperation;
import org.camunda.bpm.model.xml.impl.type.ModelElementTypeImpl;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.child.ChildElementBuilder;
import org.camunda.bpm.model.xml.type.child.ChildElementCollectionBuilder;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Daniel Meyer
 *
 */
public class SequenceBuilderImpl implements SequenceBuilder, ModelBuildOperation {

  private final ModelElementTypeImpl elementType;

  private final List<ModelBuildOperation> modelBuildOperations = new ArrayList<ModelBuildOperation>();

  public SequenceBuilderImpl(ModelElementTypeImpl modelType) {
    this.elementType = modelType;
  }

  public <T extends ModelElementInstance> ChildElementBuilder<T> element(Class<T> childElementType) {
    ChildElementBuilderImpl<T> builder = new ChildElementBuilderImpl<T>(childElementType, elementType);
    modelBuildOperations.add(builder);
    return builder;
  }

  public <T extends ModelElementInstance> ChildElementCollectionBuilder<T> elementCollection(Class<T> childElementType) {
    ChildElementCollectionBuilderImpl<T> builder = new ChildElementCollectionBuilderImpl<T>(childElementType, elementType);
    modelBuildOperations.add(builder);
    return builder;
  }

  public void performModelBuild(Model model) {
    for (ModelBuildOperation operation : modelBuildOperations) {
      operation.performModelBuild(model);
    }
  }

}
