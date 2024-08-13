/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.response.CorrelateMessageResponse;
import java.io.InputStream;
import java.util.Map;

public interface CorrelateMessageCommandStep1 {
  /**
   * Set the name of the message.
   *
   * @param messageName the name of the message
   * @return the builder for this command
   */
  CorrelateMessageCommandStep2 messageName(String messageName);

  interface CorrelateMessageCommandStep2 {
    /**
     * Set the value of the correlation key of the message.
     *
     * <p>This value will be used together with the message name to find matching message
     * subscriptions.
     *
     * @param correlationKey the correlation key value of the message
     * @return the builder for this command
     */
    CorrelateMessageCommandStep3 correlationKey(String correlationKey);

    /**
     * Skip specifying a correlation key for the message.
     *
     * <p>This method allows the message to be correlated without a correlation key, making it
     * suitable for scenarios where the correlation key is not necessary (e.g. for the message start
     * event). When used, this will create a new process instance without checking for an active
     * instance with the same correlation key.
     *
     * @return the builder for this command, continuing to the next step without a correlation key.
     */
    CorrelateMessageCommandStep3 withoutCorrelationKey();
  }

  interface CorrelateMessageCommandStep3
      extends CommandWithTenantStep<CorrelateMessageCommandStep3>,
          FinalCommandStep<CorrelateMessageResponse> {

    /**
     * Set the variables of the message.
     *
     * @param variables the variables (JSON) as stream
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CorrelateMessageCommandStep3 variables(InputStream variables);

    /**
     * Set the variables of the message.
     *
     * @param variables the variables (JSON) as String
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CorrelateMessageCommandStep3 variables(String variables);

    /**
     * Set the variables of the message.
     *
     * @param variables the variables as map
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CorrelateMessageCommandStep3 variables(Map<String, Object> variables);

    /**
     * Set the variables of the message.
     *
     * @param variables the variables as object
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CorrelateMessageCommandStep3 variables(Object variables);

    /**
     * Set a single variable of the message.
     *
     * @param key the key of the variable as string
     * @param value the value of the variable as object
     * @return the builder for this command. Call {@link #send()} to complete the command and send
     *     it to the broker.
     */
    CorrelateMessageCommandStep3 variable(String key, Object value);
  }
}
