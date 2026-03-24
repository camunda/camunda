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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.GlobalExecutionListenerResponse;
import io.camunda.client.api.search.enums.GlobalExecutionListenerCategory;
import io.camunda.client.api.search.enums.GlobalExecutionListenerElementType;
import io.camunda.client.api.search.enums.GlobalExecutionListenerEventType;
import io.camunda.client.api.search.enums.GlobalListenerSource;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.GlobalExecutionListenerResult;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GlobalExecutionListenerResponseImpl implements GlobalExecutionListenerResponse {

  private String id;
  private String type;
  private int retries;
  private List<GlobalExecutionListenerEventType> eventTypes;
  private List<GlobalExecutionListenerElementType> elementTypes;
  private List<GlobalExecutionListenerCategory> categories;
  private boolean afterNonGlobal;
  private int priority;
  private GlobalListenerSource source;

  public GlobalExecutionListenerResponse setResponse(final GlobalExecutionListenerResult result) {
    id = result.getId();
    type = result.getType();
    retries = result.getRetries() != null ? result.getRetries() : 0;
    eventTypes =
        result.getEventTypes() != null
            ? result.getEventTypes().stream()
                .map(e -> EnumUtil.convert(e, GlobalExecutionListenerEventType.class))
                .collect(Collectors.toList())
            : Collections.emptyList();
    elementTypes =
        result.getElementTypes() != null
            ? result.getElementTypes().stream()
                .map(e -> EnumUtil.convert(e, GlobalExecutionListenerElementType.class))
                .collect(Collectors.toList())
            : Collections.emptyList();
    categories =
        result.getCategories() != null
            ? result.getCategories().stream()
                .map(c -> EnumUtil.convert(c, GlobalExecutionListenerCategory.class))
                .collect(Collectors.toList())
            : Collections.emptyList();
    afterNonGlobal = result.getAfterNonGlobal() != null && result.getAfterNonGlobal();
    priority = result.getPriority() != null ? result.getPriority() : 0;
    source =
        result.getSource() != null
            ? EnumUtil.convert(result.getSource(), GlobalListenerSource.class)
            : null;
    return this;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public int getRetries() {
    return retries;
  }

  @Override
  public List<GlobalExecutionListenerEventType> getEventTypes() {
    return eventTypes;
  }

  @Override
  public List<GlobalExecutionListenerElementType> getElementTypes() {
    return elementTypes;
  }

  @Override
  public List<GlobalExecutionListenerCategory> getCategories() {
    return categories;
  }

  @Override
  public boolean getAfterNonGlobal() {
    return afterNonGlobal;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  @Override
  public GlobalListenerSource getSource() {
    return source;
  }
}
