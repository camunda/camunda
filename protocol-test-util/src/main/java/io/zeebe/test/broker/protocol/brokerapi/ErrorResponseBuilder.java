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
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import java.util.function.Consumer;

public class ErrorResponseBuilder<R> {
  protected final Consumer<MessageBuilder<R>> registrationFunction;
  protected final ErrorResponseWriter<R> commandResponseWriter;

  public ErrorResponseBuilder(
      Consumer<MessageBuilder<R>> registrationFunction, MsgPackHelper msgPackConverter) {
    this.registrationFunction = registrationFunction;
    this.commandResponseWriter = new ErrorResponseWriter<>(msgPackConverter);
  }

  public ErrorResponseBuilder<R> errorCode(ErrorCode errorCode) {
    this.commandResponseWriter.setErrorCode(errorCode);
    return this;
  }

  public ErrorResponseBuilder<R> errorData(String errorData) {
    this.commandResponseWriter.setErrorData(errorData);
    return this;
  }

  public void register() {
    registrationFunction.accept(commandResponseWriter);
  }

  /**
   * Blocks before responding; continues sending the response only when {@link
   * ResponseController#unblockNextResponse()} is called.
   */
  public ResponseController registerControlled() {
    final ResponseController controller = new ResponseController();
    commandResponseWriter.beforeResponse(controller::waitForNextJoin);
    register();
    return controller;
  }
}
