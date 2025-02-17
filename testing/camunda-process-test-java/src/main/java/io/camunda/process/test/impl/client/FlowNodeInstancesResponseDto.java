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
package io.camunda.process.test.impl.client;

import io.camunda.client.api.search.response.FlowNodeInstance;
import java.util.List;
import java.util.stream.Collectors;

public class FlowNodeInstancesResponseDto {

  private List<FlowNodeInstanceDto> items;
  private long total;

  public List<FlowNodeInstance> getItems() {
    return items.stream().map(FlowNodeInstance.class::cast).collect(Collectors.toList());
  }

  public void setItems(final List<FlowNodeInstanceDto> items) {
    this.items = items;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(final long total) {
    this.total = total;
  }
}
