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

public class IllegalBrokerResponseException extends BrokerResponseException {

  private static final long serialVersionUID = -5363931482936307555L;

  public IllegalBrokerResponseException(String message) {
    super(message);
  }

  public IllegalBrokerResponseException(String message, Throwable cause) {
    super(message, cause);
  }

  public IllegalBrokerResponseException(Throwable cause) {
    super(cause);
  }
}
