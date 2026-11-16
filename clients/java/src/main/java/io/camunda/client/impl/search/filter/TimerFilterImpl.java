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
package io.camunda.client.impl.search.filter;

import io.camunda.client.api.search.enums.TimerState;
import io.camunda.client.api.search.filter.TimerFilter;
import io.camunda.client.impl.search.request.TypedSearchRequestPropertyProvider;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.TimerStateEnum;

public class TimerFilterImpl
    extends TypedSearchRequestPropertyProvider<io.camunda.client.protocol.rest.TimerFilter>
    implements TimerFilter {

  private final io.camunda.client.protocol.rest.TimerFilter filter;

  public TimerFilterImpl() {
    filter = new io.camunda.client.protocol.rest.TimerFilter();
  }

  @Override
  public TimerFilter processInstanceKey(final Long processInstanceKey) {
    filter.setProcessInstanceKey(ParseUtil.keyToString(processInstanceKey));
    return this;
  }

  @Override
  public TimerFilter elementInstanceKey(final Long elementInstanceKey) {
    filter.setElementInstanceKey(ParseUtil.keyToString(elementInstanceKey));
    return this;
  }

  @Override
  public TimerFilter elementId(final String elementId) {
    filter.setElementId(elementId);
    return this;
  }

  @Override
  public TimerFilter state(final TimerState state) {
    filter.setState(EnumUtil.convert(state, TimerStateEnum.class));
    return this;
  }

  @Override
  protected io.camunda.client.protocol.rest.TimerFilter getSearchRequestProperty() {
    return filter;
  }
}
