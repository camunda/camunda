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

import io.camunda.client.api.response.CreateMappingRuleResponse;

public interface CreateMappingRuleCommandStep1 extends FinalCommandStep<CreateMappingRuleResponse> {

  /**
   * Set the claim name to create a mapping rule with.
   *
   * @param claimName the claimName value
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CreateMappingRuleCommandStep1 claimName(final String claimName);

  /**
   * Set the claim value to create a mapping rule with.
   *
   * @param claimValue the claimValue value
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CreateMappingRuleCommandStep1 claimValue(final String claimValue);

  /**
   * Set the name to create a mapping rule with.
   *
   * @param name the name value
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CreateMappingRuleCommandStep1 name(final String name);

  /**
   * Set the id to create a mapping rule with.
   *
   * @param mappingRuleId the mapping rule id value
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  CreateMappingRuleCommandStep1 mappingRuleId(final String mappingRuleId);
}
