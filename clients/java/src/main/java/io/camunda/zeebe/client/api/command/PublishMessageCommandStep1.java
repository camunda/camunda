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
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.PublishMessageCommandStep1}
 */
@Deprecated
public interface PublishMessageCommandStep1
    extends CommandWithCommunicationApiStep<PublishMessageCommandStep1> {

  /**
   * Set the name of the message.
   *
   * @param messageName the name of the message
   * @return the builder for this command
   */
  PublishMessageCommandStep2 messageName(String messageName);

  /**
   * @deprecated since 8.8 for removal in 8.9, replaced by {@link
   *     io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2}
   */
  @Deprecated
  interface PublishMessageCommandStep2 {
    /**
     * Set the value of the correlation key of the message.
     *
     * <p>This value will be used together with the message name to find matching message
     * subscriptions.
     *
     * @param correlationKey the correlation key value of the message
     * @return the builder for this command
     */
    PublishMessageCommandStep3 correlationKey(String correlationKey);

    /**
     * Skip specifying a correlation key for the message.
     *
     * <p>This method allows the message to be published without a correlation key, making it
     * suitable for scenarios where the correlation key is not necessary (e.g. for the message start
     * event). When used, this will create a new process instance without checking for an active
     * instance with the same correlation key.
     *
     * @return the builder for this command, continuing to the next step without a correlation key.
     */
    PublishMessageCommandStep3 withoutCorrelationKey();
  }

  /**
   * @deprecated since 8.8 for removal in 8.9, replaced by {@link
   *     io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3}
   */
  @Deprecated
  interface PublishMessageCommandStep3
      extends CommandWithTenantStep<PublishMessageCommandStep3>,
          FinalCommandStep<PublishMessageResponse> {
    /**
     * Set the id of the message. The message is rejected if another message is already published
     * with the same id, name and correlation-key.
     *
     * @param messageId the id of the message
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    PublishMessageCommandStep3 messageId(String messageId);

    /**
     * Set the time-to-live of the message. The message can only be correlated within the given
     * time-to-live.
     *
     * <p>If the duration is zero or negative then the message can only be correlated to open
     * subscriptions (e.g. to an entered message catch event).
     *
     * <p>If no duration is set then the default is used from the configuration.
     *
     * @param timeToLive the time-to-live of the message
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    PublishMessageCommandStep3 timeToLive(Duration timeToLive);

    /**
     * Set the variables of the message.
     *
     * @param variables the variables (JSON) as stream
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    PublishMessageCommandStep3 variables(InputStream variables);

    /**
     * Set the variables of the message.
     *
     * @param variables the variables (JSON) as String
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    PublishMessageCommandStep3 variables(String variables);

    /**
     * Set the variables of the message.
     *
     * @param variables the variables as map
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    PublishMessageCommandStep3 variables(Map<String, Object> variables);

    /**
     * Set the variables of the message.
     *
     * @param variables the variables as object
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    PublishMessageCommandStep3 variables(Object variables);

    /**
     * Set a single variable of the message.
     *
     * @param key the key of the variable as string
     * @param value the value of the variable as object
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    PublishMessageCommandStep3 variable(String key, Object value);
  }
}
