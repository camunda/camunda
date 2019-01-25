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

/**
 * Represents exceptional errors that occur sending a client request and/or handling its response.
 */
public class ClientResponseException extends ClientException {

  private static final long serialVersionUID = -1143986732133851047L;

  public ClientResponseException(String message) {
    super(message);
  }

  public ClientResponseException(Throwable cause) {
    super(cause);
  }

  public ClientResponseException(String message, Throwable cause) {
    super(message, cause);
  }
}
