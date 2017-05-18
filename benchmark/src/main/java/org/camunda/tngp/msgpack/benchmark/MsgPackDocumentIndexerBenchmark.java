package org.camunda.tngp.msgpack.benchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.mapping.MsgPackDocumentIndexer;

public class MsgPackDocumentIndexerBenchmark
{
    protected static MsgPackConverter converter = new MsgPackConverter();

    protected static final int LEVELS_OF_NESTING = 5;
    protected static final int NUM_VALUES_PER_LEVEL = 12;
    protected static final int RUN_COUNT = 10;

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
