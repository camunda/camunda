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
package io.camunda.client.api.command;

import io.camunda.client.api.response.ResumeProcessDefinitionResponse;

public interface ResumeProcessDefinitionCommandStep1 {

  /**
   * Selects the single process definition version to resume.
   *
   * @param processDefinitionKey the key of the process definition version
   * @return the builder for this command
   */
  ResumeProcessDefinitionCommandStep2 processDefinitionKey(long processDefinitionKey);

  /**
   * Selects the process definitions to resume by their BPMN process ID. All versions are affected
   * unless {@link ResumeProcessDefinitionCommandStep3#version(int)} is set.
   *
   * @param processDefinitionId the BPMN process ID of the process definition
   * @return the builder for this command
   */
  ResumeProcessDefinitionCommandStep3 processDefinitionId(String processDefinitionId);

  interface ResumeProcessDefinitionCommandStep2
      extends FinalCommandStep<ResumeProcessDefinitionResponse> {

    /**
     * Whether to also resume the process instances of the affected process definition versions. The
     * instances are changed by a batch operation, whose key is part of the response.
     *
     * @param includeProcessInstances true to also resume the process instances; defaults to false
     * @return the builder for this command
     */
    ResumeProcessDefinitionCommandStep2 includeProcessInstances(boolean includeProcessInstances);
  }

  interface ResumeProcessDefinitionCommandStep3 extends ResumeProcessDefinitionCommandStep2 {

    /**
     * Restricts the command to one version of the process definition.
     *
     * @param version the version, 1 or greater
     * @return the builder for this command
     */
    ResumeProcessDefinitionCommandStep3 version(int version);

    /**
     * Sets the tenant of the process definition. Defaults to the configured default tenant.
     *
     * @param tenantId the tenant ID
     * @return the builder for this command
     */
    ResumeProcessDefinitionCommandStep3 tenantId(String tenantId);

    @Override
    ResumeProcessDefinitionCommandStep3 includeProcessInstances(boolean includeProcessInstances);
  }
}
