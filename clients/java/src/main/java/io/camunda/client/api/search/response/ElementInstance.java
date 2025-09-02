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
package io.camunda.client.api.search.response;
import java.time.OffsetDateTime;

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;

public interface ElementInstance {

  /** key */
  Long getElementInstanceKey();

  /** process definition key for element instance */
  Long getProcessDefinitionKey();

  /** process definition id for element instance */
  String getProcessDefinitionId();

  /** process instance key for element instance */
  Long getProcessInstanceKey();

  /** element id for element instance */
  String getElementId();

  /** element name for element instance */
  String getElementName();

  /** start date of element instance */
  OffsetDateTime getStartDate();

  /** end date of element instance */
  OffsetDateTime getEndDate();

  /** whether element instance has an incident */
  Boolean getIncident();

  /** incident key for element instance */
  Long getIncidentKey();

  /** state of element instance */
  ElementInstanceState getState();

  /** tenant id for element instance */
  String getTenantId();

  /** type of element instance */
  ElementInstanceType getType();
}
