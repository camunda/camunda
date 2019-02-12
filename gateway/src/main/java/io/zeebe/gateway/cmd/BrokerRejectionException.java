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

import io.zeebe.gateway.impl.broker.response.BrokerRejection;

/** A client command was rejected by the broker. */
public class BrokerRejectionException extends BrokerException {
  private static final long serialVersionUID = -4363984283411850284L;
  private static final String ERROR_MESSAGE_FORMAT = "Command (%s) rejected (%s): %s";

  private final BrokerRejection rejection;

  public BrokerRejectionException(BrokerRejection rejection) {
    this(rejection, null);
  }

  public BrokerRejectionException(BrokerRejection rejection, Throwable cause) {
    super(
        String.format(
            ERROR_MESSAGE_FORMAT,
            rejection.getIntent().name(),
            rejection.getType().name(),
            rejection.getReason()),
        cause);
    this.rejection = rejection;
  }

  public BrokerRejection getRejection() {
    return rejection;
  }
}
