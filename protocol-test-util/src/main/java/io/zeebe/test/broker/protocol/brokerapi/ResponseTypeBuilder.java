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

import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class ResponseTypeBuilder<R> {

  protected final Consumer<ResponseStub<R>> stubConsumer;
  protected final Predicate<R> activationFunction;

  public ResponseTypeBuilder(
      Consumer<ResponseStub<R>> stubConsumer, Predicate<R> activationFunction) {
    this.activationFunction = activationFunction;
    this.stubConsumer = stubConsumer;
  }

  protected void respondWith(MessageBuilder<R> responseBuilder) {
    final ResponseStub<R> responseStub = new ResponseStub<>(activationFunction, responseBuilder);
    stubConsumer.accept(responseStub);
  }

  public void doNotRespond() {
    respondWith(null);
  }
}
