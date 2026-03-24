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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.enums.GlobalExecutionListenerEventType;
import io.camunda.client.api.search.enums.GlobalListenerSource;
import io.camunda.client.api.search.filter.builder.GlobalExecutionListenerEventTypeProperty;
import io.camunda.client.api.search.filter.builder.GlobalListenerSourceProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.List;
import java.util.function.Consumer;

public interface GlobalExecutionListenerFilter extends SearchRequestFilter {

  GlobalExecutionListenerFilter id(String id);

  GlobalExecutionListenerFilter id(Consumer<StringProperty> fn);

  GlobalExecutionListenerFilter type(String type);

  GlobalExecutionListenerFilter type(Consumer<StringProperty> fn);

  GlobalExecutionListenerFilter eventTypes(List<GlobalExecutionListenerEventType> eventTypes);

  GlobalExecutionListenerFilter eventTypes(GlobalExecutionListenerEventType... eventTypes);

  GlobalExecutionListenerFilter eventTypes(Consumer<GlobalExecutionListenerEventTypeProperty> fn);

  GlobalExecutionListenerFilter eventType(GlobalExecutionListenerEventType eventType);

  GlobalExecutionListenerFilter retries(Integer retries);

  GlobalExecutionListenerFilter retries(Consumer<IntegerProperty> fn);

  GlobalExecutionListenerFilter afterNonGlobal(Boolean afterNonGlobal);

  GlobalExecutionListenerFilter beforeNonGlobal();

  GlobalExecutionListenerFilter afterNonGlobal();

  GlobalExecutionListenerFilter priority(Integer priority);

  GlobalExecutionListenerFilter priority(Consumer<IntegerProperty> fn);

  GlobalExecutionListenerFilter source(GlobalListenerSource source);

  GlobalExecutionListenerFilter source(Consumer<GlobalListenerSourceProperty> fn);
}
