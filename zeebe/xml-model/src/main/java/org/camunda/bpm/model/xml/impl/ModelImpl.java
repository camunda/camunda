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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelException;
import org.camunda.bpm.model.xml.impl.util.ModelUtil;
import org.camunda.bpm.model.xml.impl.util.QName;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;

/**
 * A model contains all defined types and the relationship between them.
 *
 * @author Daniel Meyer
 */
public class ModelImpl implements Model {

  protected final Map<String, Set<String>> actualNsToAlternative = new HashMap<>();
  protected final Map<String, String> alternativeNsToActual = new HashMap<>();

  // Generic types are added while other threads look up types, see
  // ModelInstanceImpl#registerGenericType
  private final Map<QName, ModelElementType> typesByName = new ConcurrentHashMap<>();
  private final Map<Class<? extends ModelElementInstance>, ModelElementType> typesByClass =
      new ConcurrentHashMap<>();
  private final String modelName;

  /**
   * Create a new {@link Model} with a model name.
   *
   * @param modelName the model name to identify the model
   */
  public ModelImpl(final String modelName) {
    this.modelName = modelName;
  }

  /**
   * Declares an alternative namespace for an actual so that during lookup of elements/attributes
   * both will be considered. This can be used if a newer namespaces replaces an older one but XML
   * files with the old one should still be parseable.
   *
   * @param alternativeNs the alternative namespace
   * @param actualNs the actual namespace
   * @throws IllegalArgumentException if the alternative is already used or if the actual namespace
   *     has an alternative
   */
  public void declareAlternativeNamespace(final String alternativeNs, final String actualNs) {
    // linked hash set for consistent iteration order
    final Set<String> alternativeNamespaces =
        actualNsToAlternative.computeIfAbsent(actualNs, k -> new LinkedHashSet<>());

    alternativeNamespaces.add(alternativeNs);
    alternativeNsToActual.put(alternativeNs, actualNs);
  }

  public void undeclareAlternativeNamespace(final String alternativeNs) {
    if (!alternativeNsToActual.containsKey(alternativeNs)) {
      return;
    }
    final String actual = alternativeNsToActual.remove(alternativeNs);
    actualNsToAlternative.remove(actual);
  }

  @Override
  public Collection<ModelElementType> getTypes() {
    return new ArrayList<>(typesByName.values());
  }

  @Override
  public ModelElementType getType(final Class<? extends ModelElementInstance> instanceClass) {
    if (instanceClass == null) {
      // ConcurrentHashMap rejects null keys; keep returning null like the former HashMap
      return null;
    }
    return typesByClass.get(instanceClass);
  }

  @Override
  public ModelElementType getTypeForName(final String typeName) {
    return getTypeForName(null, typeName);
  }

  @Override
  public ModelElementType getTypeForName(final String namespaceUri, final String typeName) {
    return typesByName.get(ModelUtil.getQName(namespaceUri, typeName));
  }

  @Override
  public String getModelName() {
    return modelName;
  }

  @Override
  public String getActualNamespace(final String alternativeNs) {
    return alternativeNsToActual.get(alternativeNs);
  }

  @Override
  public String getAlternativeNamespace(final String actualNs) {
    final Set<String> alternatives = getAlternativeNamespaces(actualNs);

    if (alternatives == null || alternatives.isEmpty()) {
      return null;
    } else if (alternatives.size() == 1) {
      return alternatives.iterator().next();
    } else {
      throw new ModelException("There is more than one alternative namespace registered");
    }
  }

  @Override
  public Set<String> getAlternativeNamespaces(final String actualNs) {
    return actualNsToAlternative.get(actualNs);
  }

  /**
   * Registers a {@link ModelElementType} in this {@link Model}.
   *
   * @param modelElementType the element type to register
   * @param instanceType the instance class of the type to register
   */
  public void registerType(
      final ModelElementType modelElementType,
      final Class<? extends ModelElementInstance> instanceType) {
    final QName qName =
        ModelUtil.getQName(modelElementType.getTypeNamespace(), modelElementType.getTypeName());
    typesByName.put(qName, modelElementType);
    typesByClass.put(instanceType, modelElementType);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((modelName == null) ? 0 : modelName.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ModelImpl other = (ModelImpl) obj;
    if (modelName == null) {
      return other.modelName == null;
    } else {
      return modelName.equals(other.modelName);
    }
  }
}
