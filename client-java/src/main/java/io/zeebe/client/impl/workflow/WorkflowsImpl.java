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
package io.zeebe.client.impl.workflow;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.commands.Workflows;
import io.zeebe.client.impl.event.WorkflowImpl;
import java.util.List;

public class WorkflowsImpl implements Workflows {
  private List<Workflow> workflows;

  @JsonDeserialize(contentAs = WorkflowImpl.class)
  @Override
  public List<Workflow> getWorkflows() {
    return workflows;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("Workflows [");
    builder.append(workflows);
    builder.append("]");
    return builder.toString();
  }
}
