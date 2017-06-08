package org.camunda.tngp.msgpack.benchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQuery;
import org.camunda.tngp.msgpack.jsonpath.JsonPathQueryCompiler;
import org.camunda.tngp.msgpack.mapping.Mapping;
import org.camunda.tngp.msgpack.mapping.MsgPackDocumentExtractor;

public class MsgPackDocumentExtractorBenchmark
{

    protected static MsgPackConverter converter = new MsgPackConverter();

    protected static final int LEVELS_OF_NESTING = 5;
    protected static final int NUM_VALUES_PER_LEVEL = 12;
    protected static final int RUN_COUNT = 100;
    protected static final int MAPPING_COUNT = 5;

    public static void main(String[] args) throws Exception
    {
        // init
        System.out.println("== Init extractor test ==");
        final JsonGenerator generator = new JsonGenerator(LEVELS_OF_NESTING - 1, NUM_VALUES_PER_LEVEL);
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        generator.generate(outStream);

        final byte[] json = outStream.toByteArray();
        final Mapping[] mappings = generateMappings(MAPPING_COUNT, LEVELS_OF_NESTING, NUM_VALUES_PER_LEVEL);
        final byte[] msgPack = converter.convertToMsgPack(new ByteArrayInputStream(json));
        System.out.println(String.format("Message pack document size %d bytes.", msgPack.length));
        final DirectBuffer sourceDocument = new UnsafeBuffer(msgPack);
        final MsgPackDocumentExtractor extractor = new MsgPackDocumentExtractor();

        // test
        System.out.println();
        System.out.println("== Start extract test ==");

        long avgDiff = 0;
        long maxDiff = 0;
        long minDiff = Long.MAX_VALUE;
        for (int i = 0; i < RUN_COUNT; i++)
        {
            extractor.wrap(sourceDocument);
            final long startExtract = System.currentTimeMillis();
            extractor.extract(mappings);
            final long endExtract = System.currentTimeMillis();
            final long diff = endExtract - startExtract;

            avgDiff += diff;
            maxDiff = Math.max(maxDiff, diff);
            minDiff = Math.min(minDiff, diff);
        }

        avgDiff /= RUN_COUNT;
        System.out.println(String.format("Tests are run %d times.", RUN_COUNT));
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
            //            sb.append((char) (numValuesPerLevel - 1 + 65));
        }

        return sb.toString();
    }
}
