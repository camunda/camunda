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

import io.camunda.client.api.response.GlobalTaskListenerResponse;
import io.camunda.client.api.search.enums.GlobalListenerSource;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.GlobalTaskListenerResult;
import java.util.List;
import java.util.stream.Collectors;

public class GlobalTaskListenerResponseImpl implements GlobalTaskListenerResponse {
  private String id;
  private String type;
  private Integer retries;
  private List<GlobalTaskListenerEventType> eventTypes;
  private Boolean afterNonGlobal;
  private Integer priority;
  private GlobalListenerSource source;

  public GlobalTaskListenerResponse setResponse(
      final GlobalTaskListenerResult globalTaskListenerResult) {
    id = globalTaskListenerResult.getId();
    type = globalTaskListenerResult.getType();
    retries = globalTaskListenerResult.getRetries();
    eventTypes =
        globalTaskListenerResult.getEventTypes() != null
            ? globalTaskListenerResult.getEventTypes().stream()
                .map(et -> EnumUtil.convert(et, GlobalTaskListenerEventType.class))
                .collect(Collectors.toList())
            : null;
    afterNonGlobal = globalTaskListenerResult.getAfterNonGlobal();
    priority = globalTaskListenerResult.getPriority();
    source = EnumUtil.convert(globalTaskListenerResult.getSource(), GlobalListenerSource.class);
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
  public Integer getRetries() {
    return retries;
  }

  @Override
  public List<GlobalTaskListenerEventType> getEventTypes() {
    return eventTypes;
  }

  @Override
  public Boolean getAfterNonGlobal() {
    return afterNonGlobal;
  }

  @Override
  public Integer getPriority() {
    return priority;
  }

  @Override
  public GlobalListenerSource getSource() {
    return source;
  }
}
