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
package io.zeebe.msgpack.benchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.msgpack.mapping.MsgPackDocumentIndexer;

public class MsgPackDocumentIndexerBenchmark
{
    protected static MsgPackConverter converter = new MsgPackConverter();

    protected static final int LEVELS_OF_NESTING = 5;
    protected static final int NUM_VALUES_PER_LEVEL = 12;
    protected static final int RUN_COUNT = 100;

    public static void main(String[] args) throws Exception
    {
        // init
        System.out.println("== Init index test ==");
        final JsonGenerator generator = new JsonGenerator(LEVELS_OF_NESTING - 1, NUM_VALUES_PER_LEVEL);
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        generator.generate(outStream);

        final byte[] json = outStream.toByteArray();
        final byte[] msgPack = converter.convertToMsgPack(new ByteArrayInputStream(json));
        System.out.println(String.format("Message pack document size %d bytes.", msgPack.length));
        final DirectBuffer sourceDocument = new UnsafeBuffer(msgPack);
        final MsgPackDocumentIndexer indexer = new MsgPackDocumentIndexer();

        // test
        System.out.println();
        System.out.println("== Start index test ==");

        long avgDiff = 0;
        long maxDiff = 0;
        long minDiff = Long.MAX_VALUE;
        for (int i = 0; i < RUN_COUNT; i++)
        {
            indexer.wrap(sourceDocument);
            final long startIndex = System.currentTimeMillis();
            indexer.index();
            final long endIndex = System.currentTimeMillis();
            final long diff = endIndex - startIndex;

            avgDiff += diff;
            maxDiff = Math.max(maxDiff, diff);
            minDiff = Math.min(minDiff, diff);
        }

        avgDiff /= RUN_COUNT;
        System.out.println(String.format("Tests are run %d times.", RUN_COUNT));
        System.out.println(String.format("Index avg execution time: %d ms", avgDiff));
        System.out.println(String.format("Index min execution time: %d ms", minDiff));
        System.out.println(String.format("Index max execution time: %d ms", maxDiff));
    }
}
