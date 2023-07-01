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
package io.camunda.zeebe.model.bpmn.instance.zeebe;

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;

public interface ZeebePublishMessage extends BpmnModelElementInstance {

  /**
   * @return the correlation key of the message
   */
  String getCorrelationKey();

  /**
   * Sets the correlation key of the message.
   *
   * @param correlationKey the correlation key of the message
   */
  void setCorrelationKey(String correlationKey);

  /**
   * @return the id of the message
   */
  String getMessageId();

  /**
   * Sets the id of the message.
   *
   * @param messageId the id of the message
   */
  void setMessageId(String messageId);

  /**
   * @return the time to live of the message
   */
  String getTimeToLive();

  /**
   * Sets the time to live of the message.
   *
   * @param timeToLive the time to live of the message
   */
  void setTimeToLive(final String timeToLive);
}
