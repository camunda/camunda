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
package io.zeebe.gateway.cmd;

import io.zeebe.gateway.impl.broker.response.BrokerError;

public class BrokerErrorException extends BrokerException {
  private static final long serialVersionUID = 1L;
  private static final String ERROR_MESSAGE_FORMAT = "Received error from broker (%s): %s";

  protected final BrokerError error;

  public BrokerErrorException(BrokerError brokerError) {
    this(brokerError, null);
  }

  public BrokerErrorException(BrokerError error, Throwable cause) {
    super(String.format(ERROR_MESSAGE_FORMAT, error.getCode(), error.getMessage()), cause);
    this.error = error;
  }

  public BrokerError getError() {
    return error;
  }
}
