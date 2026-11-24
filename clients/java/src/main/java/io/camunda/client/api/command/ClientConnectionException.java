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

import java.net.URI;

/**
 * Thrown when the client cannot establish a connection to the Camunda gateway, for example because
 * the gateway is not running, the configured address is wrong, or the host cannot be resolved.
 *
 * <p>When this exception is thrown, the request did not reach the gateway.
 */
public final class ClientConnectionException extends ClientException {
  private static final long serialVersionUID = 1L;
  private static final String MESSAGE_FORMAT =
      "Failed to connect to the Camunda gateway at %s. Check that the gateway is running and"
          + " reachable, and that the client is configured with the correct address.";

  private final URI address;

  public ClientConnectionException(final URI address, final Throwable cause) {
    super(String.format(MESSAGE_FORMAT, address), cause);
    this.address = address;
  }

  /** The gateway address the client tried to connect to. */
  public URI address() {
    return address;
  }
}
