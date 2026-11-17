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

import io.camunda.client.api.search.enums.WaitStateType;
import io.camunda.client.api.search.response.ConditionWaitStateDetails;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ConditionWaitStateDetailsImpl implements ConditionWaitStateDetails {

  private final String expression;
  private final List<String> events;

  public ConditionWaitStateDetailsImpl(
      final io.camunda.client.protocol.rest.ConditionWaitStateDetails details) {
    expression = details.getExpression();
    events =
        details.getEvents() == null
            ? Collections.emptyList()
            : Collections.unmodifiableList(
                details.getEvents().stream()
                    .map(
                        io.camunda.client.protocol.rest.ConditionWaitStateDetails.EventsEnum
                            ::getValue)
                    .collect(Collectors.toList()));
  }

  @Override
  public WaitStateType getWaitStateType() {
    return WaitStateType.CONDITION;
  }

  @Override
  public String getExpression() {
    return expression;
  }

  @Override
  public List<String> getEvents() {
    return events;
  }
}
