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
package io.zeebe.transport;

import org.agrona.DirectBuffer;

/**
 * Response obtained to a client request. See {@link ClientOutput#sendRequest(RemoteAddress,
 * io.zeebe.util.buffer.BufferWriter)} and others.
 */
public interface ClientResponse {
  /** @return the remote address from which the response was obtained */
  RemoteAddress getRemoteAddress();

  /** @return the id of the request */
  long getRequestId();

  /** @return the response data */
  DirectBuffer getResponseBuffer();
}
