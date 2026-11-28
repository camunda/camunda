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
package io.camunda.client.impl.http;

import io.camunda.client.api.command.ClientConnectionException;
import io.camunda.client.api.command.ClientException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.hc.client5.http.ConnectTimeoutException;

/**
 * Translates failures raised by the HTTP transport into the client's public exception types, so
 * that users see a meaningful {@link ClientException} instead of a transport-library exception.
 */
final class TransportFailureTranslator {

  private final URI gatewayAddress;

  TransportFailureTranslator(final URI gatewayAddress) {
    this.gatewayAddress = gatewayAddress;
  }

  ClientException translate(final Throwable failure) {
    if (failure instanceof ClientException) {
      return (ClientException) failure;
    }

    if (isConnectionFailure(failure)) {
      return new ClientConnectionException(gatewayAddress, failure);
    }

    return new ClientException(failure);
  }

  private static boolean isConnectionFailure(final Throwable failure) {
    // cause chains can be cyclic; without this guard the loop would never terminate
    final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable current = failure;
        current != null && visited.add(current);
        current = current.getCause()) {
      if (isConnectionFailureType(current)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Only failures raised before the request was sent qualify. A plain {@link
   * java.net.SocketTimeoutException} does not, as it may be a read timeout after the gateway
   * received the request.
   */
  private static boolean isConnectionFailureType(final Throwable failure) {
    return failure instanceof ConnectException
        || failure instanceof ConnectTimeoutException
        || failure instanceof NoRouteToHostException
        || failure instanceof UnknownHostException;
  }
}
