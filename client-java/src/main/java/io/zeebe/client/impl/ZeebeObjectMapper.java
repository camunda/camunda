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
package io.zeebe.client.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.impl.data.MsgPackConverter;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * Zeebe specific {@link ObjectMapper} that internally uses {@link MessagePackFactory} and {@link MsgPackConverter} to shrink
 * json messages.
 *
 * Does not support {@link #writeValueAsString(Object)}!
 */
public class ZeebeObjectMapper extends ObjectMapper
{

    protected final MsgPackConverter msgPackConverter;
    protected final InjectableValues.Std injectableValues;

    public ZeebeObjectMapper()
    {
        super(new MessagePackFactory()
            .setReuseResourceInGenerator(false)
            .setReuseResourceInParser(false));

        this.msgPackConverter = new MsgPackConverter();

        setSerializationInclusion(JsonInclude.Include.NON_NULL);
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        this.injectableValues = new InjectableValues.Std().addValue(MsgPackConverter.class, msgPackConverter);
        setInjectableValues(injectableValues);
    }

    /**
     * This method is not applicable for the {@link ZeebeObjectMapper} due to the used msgPack. Use {@link #writeValueAsBytes(Object)} instead.
     *
     * @throws UnsupportedOperationException
     * @see #writeValueAsBytes(Object)
     * @param value the value to write
     * @return fails due to unsupported operation
     */
    @Override
    public String writeValueAsString(final Object value)
    {
        throw new UnsupportedOperationException("The msgpack dataformat this mapper uses does not support 'writeValueAsString'. Use 'writeValueAsBytes'");
    }

    public MsgPackConverter getMsgPackConverter()
    {
        return msgPackConverter;
    }
}
