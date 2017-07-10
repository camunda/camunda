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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MappingProcessor;

public class MappingExtractBenchmark
{

    protected static MsgPackConverter converter = new MsgPackConverter();
    protected static final int LEVELS_OF_NESTING = 5;
    protected static final int NUM_VALUES_PER_LEVEL = 12;
    protected static final int MAPPING_COUNT = 5;
    protected static final int RUN_COUNT = 10;

    public static void main(String[] args) throws Exception
    {
        // init
        System.out.println("== Init mapping extract test ==");
        final JsonGenerator generator = new JsonGenerator(LEVELS_OF_NESTING - 1, NUM_VALUES_PER_LEVEL);
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        generator.generate(outStream);

        final byte[] json = outStream.toByteArray();

        final Mapping[] mappings = generateMappings(MAPPING_COUNT, LEVELS_OF_NESTING, NUM_VALUES_PER_LEVEL);
        final byte[] msgPack = converter.convertToMsgPack(new ByteArrayInputStream(json));
        System.out.println(String.format("Message pack document size %d bytes.", msgPack.length));
        final DirectBuffer sourceDocument = new UnsafeBuffer(msgPack);
        final MappingProcessor processor = new MappingProcessor(256);

        // test
        System.out.println();
        System.out.println("== Start extract test ==");

        long avgDiff = 0;
        long maxDiff = 0;
        long minDiff = Long.MAX_VALUE;
        long avgWrittenBytes = 0;
        for (int i = 0; i < RUN_COUNT; i++)
        {
            final long startExtract = System.currentTimeMillis();
            final int resultLen = processor.extract(sourceDocument, mappings);
            final long endExtract = System.currentTimeMillis();
            final long diff = endExtract - startExtract;

            avgDiff += diff;
            avgWrittenBytes += resultLen;
            maxDiff = Math.max(maxDiff, diff);
            minDiff = Math.min(minDiff, diff);
        }

        avgDiff /= RUN_COUNT;
        avgWrittenBytes /= RUN_COUNT;
        System.out.println(String.format("Tests are run %d times.", RUN_COUNT));
        System.out.println(String.format("AVG written bytes: %d", avgWrittenBytes));
        System.out.println();
        System.out.println(String.format("Extract avg execution time: %d ms", avgDiff));
        System.out.println(String.format("Extract min execution time: %d ms", minDiff));
        System.out.println(String.format("Extract max execution time: %d ms", maxDiff));
    }

    protected static Mapping[] generateMappings(int mappingCount, int levelsOfNesting, int numValuesPerLevel)
    {
        final JsonPathQuery rootSourceQuery = new JsonPathQueryCompiler().compile("$");

        final List<Mapping> mappings = new ArrayList<>();
        for (int i = 0; i < mappingCount; i++)
        {
            mappings.add(new Mapping(rootSourceQuery,
                                     generateJsonPathExpression(levelsOfNesting, numValuesPerLevel)));
        }
        return mappings.toArray(new Mapping[mappingCount]);
    }

    private static String generateJsonPathExpression(int levelsOfNesting, int numValuesPerLevel)
    {
        final Random r = new Random();
        final StringBuilder sb = new StringBuilder();
        sb.append("$");
        for (int i = 0; i < levelsOfNesting; i++)
        {
            sb.append(".");
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            sb.append((char) (r.nextInt(numValuesPerLevel) + 65));
            //            sb.append((char) (numValuesPerLevel - 1 + 65));
        }

        return sb.toString();
    }
}
