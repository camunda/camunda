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
package io.zeebe.broker.benchmarks.msgpack;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MappingProcessor;
import io.zeebe.util.ByteUnit;
import io.zeebe.util.buffer.BufferUtil;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class MappingCtx
{
    public MappingProcessor processor;
    public Mapping[] rootMappings;

    @Setup
    public void setup()
    {
        processor = new MappingProcessor((int) ByteUnit.KILOBYTES.toBytes(16));
        final JsonPathQuery rootSourceQuery = new JsonPathQueryCompiler().compile("$");
        rootMappings = new Mapping[]{new Mapping(rootSourceQuery, BufferUtil.wrapString("$"))};
    }
}
