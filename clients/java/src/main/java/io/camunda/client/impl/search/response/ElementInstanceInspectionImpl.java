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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.response.ElementInstanceInspection;
import io.camunda.client.api.search.response.WaitState;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.ElementInstanceInspectionResult;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ElementInstanceInspectionImpl implements ElementInstanceInspection {

  private final Long elementInstanceKey;
  private final String elementId;
  private final String elementType;
  private final List<WaitState> waitStates;

  public ElementInstanceInspectionImpl(final ElementInstanceInspectionResult item) {
    elementInstanceKey = ParseUtil.parseLongOrNull(item.getElementInstanceKey());
    elementId = item.getElementId();
    elementType = item.getElementType();
    waitStates = item.getWaitStates().stream().map(WaitStateImpl::new).collect(Collectors.toList());
  }

  @Override
  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public String getElementType() {
    return elementType;
  }

  @Override
  public List<WaitState> getWaitStates() {
    return waitStates;
  }

  @Override
  public int hashCode() {
    return Objects.hash(elementInstanceKey, elementId, elementType, waitStates);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ElementInstanceInspectionImpl that = (ElementInstanceInspectionImpl) o;
    return Objects.equals(elementInstanceKey, that.elementInstanceKey)
        && Objects.equals(elementId, that.elementId)
        && Objects.equals(elementType, that.elementType)
        && Objects.equals(waitStates, that.waitStates);
  }
}
