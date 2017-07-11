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
package io.zeebe.msgpack.benchmark;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.query.MsgPackQueryExecutor;
import io.zeebe.msgpack.query.MsgPackTraverser;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

public class JsonConversionMsgPackJsonPathProcessor implements JsonPathProcessor
{

    protected MsgPackConverter converter = new MsgPackConverter();
    protected JsonPathQueryCompiler queryCompiler = new JsonPathQueryCompiler();
    protected MsgPackTraverser traverser = new MsgPackTraverser();
    protected MsgPackQueryExecutor queryExecutor = new MsgPackQueryExecutor();
    protected UnsafeBuffer msgPackBuffer = new UnsafeBuffer(0, 0);

    @Override
    public String evaluateJsonPath(byte[] json, String jsonPath) throws Exception
    {
        final InputStream jsonStream = new ByteArrayInputStream(json);

        final byte[] msgPack = converter.convertToMsgPack(jsonStream);

        msgPackBuffer.wrap(msgPack);

        final JsonPathQuery query = queryCompiler.compile(jsonPath);
        queryExecutor.init(query.getFilters(), query.getFilterInstances());
        traverser.wrap(msgPackBuffer, 0, msgPackBuffer.capacity());
        traverser.traverse(queryExecutor);
        queryExecutor.moveToResult(0);

        final int resultLength = queryExecutor.currentResultLength();
        final byte[] result = new byte[resultLength];

        System.arraycopy(msgPack, queryExecutor.currentResultPosition(), result, 0, resultLength);
        final MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(result);

        return Integer.toString(unpacker.unpackInt());
    }

}
