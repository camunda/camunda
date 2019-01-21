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
 * Represents exceptional errors that occur in the gateway-broker client on the broker side, e.g.
 * error responses, command rejections, etc.
 *
 * <p>Primary usage is wrapping around error responses so that these can be consumed by throwable
 * handlers.
 */
public class BrokerException extends RuntimeException {

  private static final long serialVersionUID = -2808029505078161668L;

  public BrokerException(String message) {
    super(message);
  }

  public BrokerException(String message, Throwable cause) {
    super(message, cause);
  }

  public BrokerException(Throwable cause) {
    super(cause);
  }
}
