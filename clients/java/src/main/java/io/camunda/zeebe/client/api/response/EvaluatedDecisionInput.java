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
package io.camunda.zeebe.client.api.response;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link
 *     io.camunda.client.api.response.EvaluatedDecisionInput}
 */
@Deprecated
public interface EvaluatedDecisionInput {

  /**
   * @return the id of the evaluated decision input
   */
  String getInputId();

  /**
   * @return the name of the evaluated decision input
   */
  String getInputName();

  /**
   * @return the value of the evaluated decision input
   */
  String getInputValue();

  /**
   * @return the record encoded as JSON
   */
  String toJson();
}
