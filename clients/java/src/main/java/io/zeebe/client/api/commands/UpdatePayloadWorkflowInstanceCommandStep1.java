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
package io.zeebe.client.api.commands;

import java.io.InputStream;
import java.util.Map;

public interface UpdatePayloadWorkflowInstanceCommandStep1 {
  /**
   * Set the new payload of the workflow instance.
   *
   * @param payload the payload (JSON) as stream
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdatePayloadWorkflowInstanceCommandStep2 payload(InputStream payload);

  /**
   * Set the new payload of the workflow instance.
   *
   * @param payload the payload (JSON) as String
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdatePayloadWorkflowInstanceCommandStep2 payload(String payload);

  /**
   * Set the new payload of the workflow instance.
   *
   * @param payload the payload as map
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdatePayloadWorkflowInstanceCommandStep2 payload(Map<String, Object> payload);

  /**
   * Set the new payload of the workflow instance.
   *
   * @param payload the payload as object
   * @return the builder for this command. Call {@link #send()} to complete the command and send it
   *     to the broker.
   */
  UpdatePayloadWorkflowInstanceCommandStep2 payload(Object payload);

  interface UpdatePayloadWorkflowInstanceCommandStep2 extends FinalCommandStep<Void> {
    // the place for new optional parameters
  }
}
