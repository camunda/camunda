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

import io.camunda.client.api.search.enums.GlobalListenerSource;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.client.api.search.response.GlobalTaskListener;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.GlobalTaskListenerResult;
import java.util.List;
import java.util.stream.Collectors;

public class GlobalTaskListenerImpl implements GlobalTaskListener {
  private final String id;
  private final String type;
  private final Integer retries;
  private final List<GlobalTaskListenerEventType> eventTypes;
  private final Boolean afterNonGlobal;
  private final Integer priority;
  private final GlobalListenerSource source;

  public GlobalTaskListenerImpl(final GlobalTaskListenerResult globalTaskListenerResult) {
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
