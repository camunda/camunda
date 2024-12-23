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
package io.camunda.spring.client.jobhandling.parameter;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import java.util.Map;

public class CompatActivatedJobParameterResolver implements ParameterResolver {

  @Override
  public Object resolve(final JobClient jobClient, final ActivatedJob job) {
    return new ActivatedJobProxy(job);
  }

  public static class ActivatedJobProxy
      implements io.camunda.zeebe.client.api.response.ActivatedJob {
    private final ActivatedJob job;

    public ActivatedJobProxy(final ActivatedJob job) {
      this.job = job;
    }

    @Override
    public long getKey() {
      return job.getKey();
    }

    @Override
    public String getType() {
      return job.getType();
    }

    @Override
    public long getProcessInstanceKey() {
      return job.getProcessInstanceKey();
    }

    @Override
    public String getBpmnProcessId() {
      return job.getBpmnProcessId();
    }

    @Override
    public int getProcessDefinitionVersion() {
      return job.getProcessDefinitionVersion();
    }

    @Override
    public long getProcessDefinitionKey() {
      return job.getProcessDefinitionKey();
    }

    @Override
    public String getElementId() {
      return job.getElementId();
    }

    @Override
    public long getElementInstanceKey() {
      return job.getElementInstanceKey();
    }

    @Override
    public Map<String, String> getCustomHeaders() {
      return job.getCustomHeaders();
    }

    @Override
    public String getWorker() {
      return job.getWorker();
    }

    @Override
    public int getRetries() {
      return job.getRetries();
    }

    @Override
    public long getDeadline() {
      return job.getDeadline();
    }

    @Override
    public String getVariables() {
      return job.getVariables();
    }

    @Override
    public Map<String, Object> getVariablesAsMap() {
      return job.getVariablesAsMap();
    }

    @Override
    public <T> T getVariablesAsType(final Class<T> variableType) {
      return job.getVariablesAsType(variableType);
    }

    @Override
    public Object getVariable(final String name) {
      return job.getVariable(name);
    }

    @Override
    public String toJson() {
      return job.toJson();
    }

    @Override
    public String getTenantId() {
      return job.getTenantId();
    }
  }
}
