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

import io.camunda.client.api.response.ResumeProcessDefinitionResponse;
import io.camunda.client.api.response.ResumeProcessDefinitionVersionResult;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.ProcessDefinitionResumptionResult;
import io.camunda.client.protocol.rest.ProcessDefinitionResumptionVersionResult;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ResumeProcessDefinitionResponseImpl implements ResumeProcessDefinitionResponse {

  private final List<ResumeProcessDefinitionVersionResult> processDefinitions;
  private final String batchOperationKey;

  public ResumeProcessDefinitionResponseImpl(final ProcessDefinitionResumptionResult response) {
    processDefinitions =
        Optional.ofNullable(response.getProcessDefinitions())
            .orElseGet(Collections::emptyList)
            .stream()
            .<ResumeProcessDefinitionVersionResult>map(VersionResult::new)
            .collect(Collectors.toList());
    batchOperationKey = response.getBatchOperationKey();
  }

  @Override
  public List<ResumeProcessDefinitionVersionResult> getProcessDefinitions() {
    return processDefinitions;
  }

  @Override
  public String getBatchOperationKey() {
    return batchOperationKey;
  }

  private static final class VersionResult implements ResumeProcessDefinitionVersionResult {

    private final long processDefinitionKey;
    private final int processDefinitionVersion;
    private final Outcome outcome;

    private VersionResult(final ProcessDefinitionResumptionVersionResult result) {
      processDefinitionKey = Long.parseLong(result.getProcessDefinitionKey());
      processDefinitionVersion = result.getProcessDefinitionVersion();
      outcome = EnumUtil.convert(result.getOutcome(), Outcome.class);
    }

    @Override
    public long getProcessDefinitionKey() {
      return processDefinitionKey;
    }

    @Override
    public int getProcessDefinitionVersion() {
      return processDefinitionVersion;
    }

    @Override
    public Outcome getOutcome() {
      return outcome;
    }
  }
}
