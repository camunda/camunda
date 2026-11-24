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

import io.camunda.client.api.command.ClientException;

/**
 * Translates failures raised by the HTTP transport into the client's public exception types, so
 * that users see a meaningful {@link ClientException} instead of a transport-library exception.
 */
final class TransportFailureTranslator {

  ClientException translate(final Throwable failure) {
    if (failure instanceof ClientException) {
      return (ClientException) failure;
    }

    return new ClientException(failure);
  }
}
