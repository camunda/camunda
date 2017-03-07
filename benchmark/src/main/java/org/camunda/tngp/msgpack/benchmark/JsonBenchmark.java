package org.camunda.tngp.msgpack.benchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import com.jayway.jsonpath.spi.cache.CacheProvider;
import com.jayway.jsonpath.spi.cache.NOOPCache;

public class JsonBenchmark
{

    static
    {
        CacheProvider.setCache(new NOOPCache());
    }

    protected static final JsonPathProcessor[] JSON_PATH_JSON_PROCESSORS = new JsonPathProcessor[]{
        new JaywayJsonPathProcessor(),
        new JsonConversionMsgPackJsonPathProcessor(),
    };

    protected static final JsonPathProcessor[] JSON_PATH_MSGPACK_PROCESSORS = new JsonPathProcessor[]{
        new MsgPackJaywayJsonPathProcessor(),
        new MsgPackJsonPathProcessor(),
    };

    protected static MsgPackConverter converter = new MsgPackConverter();

    public static void main(String[] args) throws Exception
    {
//        int levelsOfNesting = 5;
//        int numValuesPerLevel = 26; // cannot be more than 26 to avoid invalid characters :)

        final int levelsOfNesting = 6;
        final int numValuesPerLevel = 10;


        final JsonGenerator generator = new JsonGenerator(levelsOfNesting - 1, numValuesPerLevel);
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        generator.generate(outStream);

        final byte[] json = outStream.toByteArray();
        System.out.println("Size: " + json.length);
        final String jsonPathExpression = generateJsonPathExpression(levelsOfNesting, numValuesPerLevel);

//        System.out.println(new String(json, StandardCharsets.UTF_8));
        System.out.println(jsonPathExpression);


        final long[] jsonBasedResults = runBenchmark(json, jsonPathExpression, JSON_PATH_JSON_PROCESSORS);

        final byte[] msgPack = converter.convertToMsgPack(new ByteArrayInputStream(json));

        final long[] msgPackBasedResults = runBenchmark(msgPack, jsonPathExpression, JSON_PATH_MSGPACK_PROCESSORS);

        System.out.println("Benchmark results:");
        printResults(jsonBasedResults, JSON_PATH_JSON_PROCESSORS);
        printResults(msgPackBasedResults, JSON_PATH_MSGPACK_PROCESSORS);

    }

    protected static long[] runBenchmark(byte[] data, String jsonPath, JsonPathProcessor[] processors) throws Exception
    {
        final long[] results = new long[processors.length];
        for (int i = 0; i < processors.length; i++)
        {
            final JsonPathProcessor processor = processors[i];
            final long startTime = System.currentTimeMillis();
            final String result = processor.evaluateJsonPath(data, jsonPath);
            final long endTime = System.currentTimeMillis();
            results[i] = endTime - startTime;
            System.out.println(result);
            System.gc();
            System.gc();
            System.gc();
        }
        return results;
    }

    protected static void printResults(long[] results, JsonPathProcessor[] processors)
    {

        for (int i = 0; i < processors.length; i++)
        {
            System.out.println(processors[i].getClass().getSimpleName() + ": " + results[i] + " ms");
        }
    }

    protected static String generateJsonPathExpression(int levelsOfNesting, int numValuesPerLevel)
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
