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

import io.zeebe.msgpack.mapping.MappingCtx;
import io.zeebe.msgpack.mapping.MsgPackMergeTool;
import java.util.concurrent.TimeUnit;
import org.agrona.DirectBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@Fork(1)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class MergeBehvaiorBenchmark {

  @Benchmark
  @Threads(1)
  public int overwriteDocument(final MappingCtx mappingCtx, final MsgPackDocuments documents) {
    final MsgPackMergeTool mergeTool = mappingCtx.processor;

    mergeTool.reset();
    mergeTool.mergeDocument(documents.targetDocument);
    mergeTool.mergeDocument(documents.sourceDocument, mappingCtx.rootMappings);

    final DirectBuffer result = mergeTool.writeResultToBuffer();
    return result.capacity();
  }

  @Benchmark
  @Threads(1)
  public int topLevelMergeViaMappings(
      final MappingCtx mappingCtx, final MsgPackDocuments documents) {
    final MsgPackMergeTool mergeTool = mappingCtx.processor;

    mergeTool.reset();
    mergeTool.mergeDocument(documents.targetDocument);
    mergeTool.mergeDocument(documents.sourceDocument, documents.mappings);

    final DirectBuffer result = mergeTool.writeResultToBuffer();
    return result.capacity();
  }

  @Benchmark
  @Threads(1)
  public int topLevelMergeDefaultBehavior(
      final MappingCtx mappingCtx, final MsgPackDocuments documents) {
    final MsgPackMergeTool mergeTool = mappingCtx.processor;

    mergeTool.reset();
    mergeTool.mergeDocument(documents.targetDocument);
    mergeTool.mergeDocument(documents.sourceDocument);

    final DirectBuffer result = mergeTool.writeResultToBuffer();
    return result.capacity();
  }
}
