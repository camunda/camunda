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
package io.zeebe.msgpack.mapping.merge;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.mapping.JsonGenerator;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MsgPackConverter;
import io.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class MsgPackDocuments {
  @Param({"100", "1000", "10000"})
  public int keyCount;

  private final int maxDepth = 0;

  UnsafeBuffer targetDocument;
  UnsafeBuffer sourceDocument;
  Mapping[] mappings;

  @Setup
  public void setUp() throws Exception {
    final JsonGenerator targetDocumentGenerator = new JsonGenerator(maxDepth, keyCount, "b");
    targetDocument = new UnsafeBuffer(generateMsgPack(targetDocumentGenerator));

    final JsonGenerator sourceDocumentGenerator = new JsonGenerator(maxDepth, keyCount);
    sourceDocument = new UnsafeBuffer(generateMsgPack(sourceDocumentGenerator));
    mappings = generateMappings(keyCount);
  }

  private static byte[] generateMsgPack(JsonGenerator generator) throws Exception {
    final MsgPackConverter converter = new MsgPackConverter();
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    generator.generate(outStream);

    final byte[] json = outStream.toByteArray();
    return converter.convertToMsgPack(new ByteArrayInputStream(json));
  }

  protected static Mapping[] generateMappings(int mappingCount) {
    final List<Mapping> mappings = new ArrayList<>();
    for (int i = 0; i < mappingCount; i++) {
      final String key = "$.a" + i;
      final JsonPathQuery rootSourceQuery = new JsonPathQueryCompiler().compile(key);
      mappings.add(new Mapping(rootSourceQuery, BufferUtil.wrapString(key), Mapping.Type.PUT));
    }
    return mappings.toArray(new Mapping[mappingCount]);
  }
}
