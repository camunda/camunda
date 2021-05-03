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
package io.zeebe.model.bpmn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.model.xml.impl.type.ModelElementTypeImpl;
import org.camunda.bpm.model.xml.type.ModelElementType;

public class BpmnTypeHierarchy {

  private final Map<ModelElementType, List<ModelElementType>> hierarchyCache = new HashMap<>();

  public void registerType(final ModelElementType type) {
    final List<ModelElementType> hierarchy = new ArrayList<>();
    hierarchy.add(type);
    ((ModelElementTypeImpl) type).resolveBaseTypes(hierarchy);
    Collections.reverse(hierarchy);
    hierarchyCache.put(type, hierarchy);
  }

  /**
   * @return the argument type and all its the types it extends. Ordering is top-down, i.e. most
   *     high-level type is first element in list.
   */
  public List<ModelElementType> getHierarchy(final ModelElementType type) {
    return hierarchyCache.get(type);
  }
}
