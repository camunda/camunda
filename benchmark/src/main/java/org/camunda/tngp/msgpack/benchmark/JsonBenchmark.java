package org.camunda.tngp.msgpack.benchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import org.camunda.tngp.client.impl.data.DocumentConverter;
import org.camunda.tngp.client.impl.data.JacksonDocumentConverter;

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

    protected static DocumentConverter converter = JacksonDocumentConverter.newDefaultConverter();

    public static void main(String[] args) throws Exception
    {
//        int levelsOfNesting = 5;
//        int numValuesPerLevel = 26; // cannot be more than 26 to avoid invalid characters :)

        int levelsOfNesting = 6;
        int numValuesPerLevel = 10;


        JsonGenerator generator = new JsonGenerator(levelsOfNesting - 1, numValuesPerLevel);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        generator.generate(outStream);

        byte[] json = outStream.toByteArray();
        System.out.println("Size: " + json.length);
        String jsonPathExpression = generateJsonPathExpression(levelsOfNesting, numValuesPerLevel);

//        System.out.println(new String(json, StandardCharsets.UTF_8));
        System.out.println(jsonPathExpression);


        long[] jsonBasedResults = runBenchmark(json, jsonPathExpression, JSON_PATH_JSON_PROCESSORS);


        ByteArrayOutputStream msgPackOutStream = new ByteArrayOutputStream();
        converter.convertToMsgPack(new ByteArrayInputStream(json), msgPackOutStream);
        byte[] msgPack = msgPackOutStream.toByteArray();

        long[] msgPackBasedResults = runBenchmark(msgPack, jsonPathExpression, JSON_PATH_MSGPACK_PROCESSORS);

        System.out.println("Benchmark results:");
        printResults(jsonBasedResults, JSON_PATH_JSON_PROCESSORS);
        printResults(msgPackBasedResults, JSON_PATH_MSGPACK_PROCESSORS);

    }

    protected static long[] runBenchmark(byte[] data, String jsonPath, JsonPathProcessor[] processors) throws Exception
    {
        long[] results = new long[processors.length];
        for (int i = 0; i < processors.length; i++)
        {
            JsonPathProcessor processor = processors[i];
            long startTime = System.currentTimeMillis();
            String result = processor.evaluateJsonPath(data, jsonPath);
            long endTime = System.currentTimeMillis();
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
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
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
