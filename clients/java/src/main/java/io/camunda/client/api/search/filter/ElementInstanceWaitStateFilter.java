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

import io.camunda.client.api.search.enums.WaitStateElementType;
import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.filter.builder.BasicStringProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.filter.builder.WaitStateElementTypeProperty;
import io.camunda.client.api.search.filter.builder.WaitStateTypeProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface ElementInstanceWaitStateFilter extends SearchRequestFilter {

  ElementInstanceWaitStateFilter elementInstanceKey(long value);

  ElementInstanceWaitStateFilter elementInstanceKey(Consumer<BasicStringProperty> fn);

  ElementInstanceWaitStateFilter processInstanceKey(long value);

  ElementInstanceWaitStateFilter processInstanceKey(Consumer<BasicStringProperty> fn);

  ElementInstanceWaitStateFilter rootProcessInstanceKey(long value);

  ElementInstanceWaitStateFilter rootProcessInstanceKey(Consumer<BasicStringProperty> fn);

  ElementInstanceWaitStateFilter elementId(String value);

  ElementInstanceWaitStateFilter elementId(Consumer<StringProperty> fn);

  ElementInstanceWaitStateFilter elementType(WaitStateElementType value);

  ElementInstanceWaitStateFilter elementType(Consumer<WaitStateElementTypeProperty> fn);

  ElementInstanceWaitStateFilter waitStateType(WaitStateType value);

  ElementInstanceWaitStateFilter waitStateType(Consumer<WaitStateTypeProperty> fn);
}
