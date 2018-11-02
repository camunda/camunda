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
package io.zeebe.msgpack.mapping.throughput;

import io.zeebe.msgpack.mapping.JsonGenerator;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MappingBuilder;
import io.zeebe.msgpack.mapping.MsgPackConverter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class ThroughputCtx {

  @Param({"3"})
  public int maxDepth;

  @Param({"5"})
  public int keyCount;

  @Param({"1"})
  public double mappingKeyPercentage;

  @Param({"1"})
  public double mappingDepthPercentage;

  UnsafeBuffer targetDocument;
  UnsafeBuffer sourceDocument;
  Mapping[] mappings;

  @Setup
  public void setUp() throws Exception {
    final JsonGenerator targetDocumentGenerator = new JsonGenerator(maxDepth, keyCount, "b");
    targetDocument = new UnsafeBuffer(generateMsgPack(targetDocumentGenerator));

    final JsonGenerator sourceDocumentGenerator = new JsonGenerator(maxDepth, keyCount);
    sourceDocument = new UnsafeBuffer(generateMsgPack(sourceDocumentGenerator));
    mappings = generateMappings(keyCount, mappingKeyPercentage, maxDepth, mappingDepthPercentage);
  }

  private static byte[] generateMsgPack(JsonGenerator generator) throws Exception {
    final MsgPackConverter converter = new MsgPackConverter();
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    generator.generate(outStream);

    final byte[] json = outStream.toByteArray();
    return converter.convertToMsgPack(new ByteArrayInputStream(json));
  }

  protected static Mapping[] generateMappings(
      int mappingCount, double countPercentage, int depth, double depthPercentage) {
    if (depthPercentage == 0) {
      return new Mapping[0];
    }

    int currentDepth = 0;
    final StringBuilder builder = new StringBuilder("$");
    final int endDepth = (int) (depth * depthPercentage);
    final int endCount = (int) (mappingCount * countPercentage);

    final MappingBuilder mappingBuilder = new MappingBuilder();

    do {
      builder.append(".a");

      for (int i = 0; i < endCount; i++) {
        final int val = (int) Math.pow(mappingCount, depth - currentDepth) * i;
        final int identifier = depth > 0 ? val : i;
        final String key = builder.toString() + identifier;

        mappingBuilder.mapping(key, key);
      }
      builder.append("0");
      currentDepth++;
    } while (currentDepth < endDepth);

    return mappingBuilder.build();
  }
}
