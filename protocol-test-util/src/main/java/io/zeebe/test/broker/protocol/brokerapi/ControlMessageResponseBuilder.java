/**
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

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.util.collection.MapFactoryBuilder;

public class ControlMessageResponseBuilder
{

    protected final Consumer<ResponseStub<ControlMessageRequest>> registrationFunction;
    protected final ControlMessageResponseWriter responseWriter;
    protected final Predicate<ControlMessageRequest> activationFunction;

    public ControlMessageResponseBuilder(
            Consumer<ResponseStub<ControlMessageRequest>> registrationFunction,
            MsgPackHelper msgPackConverter,
            Predicate<ControlMessageRequest> activationFunction)
    {
        this.registrationFunction = registrationFunction;
        this.responseWriter = new ControlMessageResponseWriter(msgPackConverter);
        this.activationFunction = activationFunction;
    }

    public ControlMessageResponseBuilder respondWith()
    {
        // syntactic sugar
        return this;
    }

    public ControlMessageResponseBuilder data(Map<String, Object> map)
    {
        responseWriter.setDataFunction((re) -> map);
        return this;
    }

    public MapFactoryBuilder<ControlMessageRequest, ControlMessageResponseBuilder> data()
    {
        return new MapFactoryBuilder<>(this, responseWriter::setDataFunction);
    }

    public void register()
    {
        registrationFunction.accept(new ResponseStub<>(activationFunction, responseWriter));
    }
}
