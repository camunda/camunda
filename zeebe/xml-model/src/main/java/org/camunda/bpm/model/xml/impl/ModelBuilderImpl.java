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
package org.camunda.bpm.model.xml.impl;

import static org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;

import java.util.ArrayList;
import java.util.List;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelElementInstanceImpl;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.impl.type.ModelElementTypeBuilderImpl;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;

/**
 * This builder is used to define and create a new model.
 *
 * @author Daniel Meyer
 */
public class ModelBuilderImpl extends ModelBuilder {

  private final List<ModelElementTypeBuilderImpl> unbuiltTypeBuilders =
      new ArrayList<ModelElementTypeBuilderImpl>();
  private final ModelImpl model;

  public ModelBuilderImpl(final String modelName) {
    model = new ModelImpl(modelName);
  }

  @Override
  public ModelBuilder alternativeNamespace(final String alternativeNs, final String actualNs) {
    model.declareAlternativeNamespace(alternativeNs, actualNs);
    return this;
  }

  @Override
  public ModelElementTypeBuilder defineType(
      final Class<? extends ModelElementInstance> modelInstanceType, final String typeName) {
    final ModelElementTypeBuilderImpl typeBuilder =
        new ModelElementTypeBuilderImpl(modelInstanceType, typeName, model);
    unbuiltTypeBuilders.add(typeBuilder);
    return typeBuilder;
  }

  @Override
  public ModelElementType defineGenericType(final String typeName, final String typeNamespaceUri) {
    final ModelElementTypeBuilder typeBuilder =
        defineType(ModelElementInstance.class, typeName)
            .namespaceUri(typeNamespaceUri)
            .instanceProvider(
                new ModelTypeInstanceProvider<ModelElementInstance>() {
                  @Override
                  public ModelElementInstance newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new ModelElementInstanceImpl(instanceContext);
                  }
                });

    return typeBuilder.build();
  }

  /**
   * Builds the types defined since the last call and returns the model.
   *
   * <p>Types that were already built are skipped. Building a type again would register its
   * references a second time, so the reference lists of the model would grow each time a generic
   * type is added (see {@link ModelInstanceImpl#registerGenericType(String, String)}).
   */
  @Override
  public Model build() {
    for (final ModelElementTypeBuilderImpl typeBuilder : unbuiltTypeBuilders) {
      typeBuilder.buildTypeHierarchy(model);
    }
    for (final ModelElementTypeBuilderImpl typeBuilder : unbuiltTypeBuilders) {
      typeBuilder.performModelBuild(model);
    }
    unbuiltTypeBuilders.clear();
    return model;
  }
}
