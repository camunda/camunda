package org.camunda.tngp.msgpack.benchmark;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.mapping.MsgPackDocumentIndexer;
import org.camunda.tngp.msgpack.mapping.MsgPackDocumentTreeWriter;
import org.camunda.tngp.msgpack.mapping.MsgPackTree;

public class MsgPackDocumentTreeWriterBenchmark
{
    protected static MsgPackConverter converter = new MsgPackConverter();

    protected static final int LEVELS_OF_NESTING = 5;
    protected static final int NUM_VALUES_PER_LEVEL = 12;
    protected static final int RUN_COUNT = 100;

    public static void main(String[] args) throws Exception
    {
        // init
        System.out.println("== Init write tree test ==");
        final JsonGenerator generator = new JsonGenerator(LEVELS_OF_NESTING - 1, NUM_VALUES_PER_LEVEL);
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        generator.generate(outStream);

        final byte[] json = outStream.toByteArray();
        final byte[] msgPack = converter.convertToMsgPack(new ByteArrayInputStream(json));
        System.out.println(String.format("Message pack document size %d bytes.", msgPack.length));
        final DirectBuffer sourceDocument = new UnsafeBuffer(msgPack);
        final MsgPackDocumentIndexer indexer = new MsgPackDocumentIndexer();
        indexer.wrap(sourceDocument);
        final MsgPackTree msgPackTree = indexer.index();
        final MsgPackDocumentTreeWriter writer = new MsgPackDocumentTreeWriter(1024);


        // test
        System.out.println();
        System.out.println("== Start write tree test ==");

        long avgDiff = 0;
        long maxDiff = 0;
        long minDiff = Long.MAX_VALUE;
        long avgWrittenBytes = 0;
        for (int i = 0; i < RUN_COUNT; i++)
        {
            final long startWrite = System.currentTimeMillis();
            final int resultLen = writer.write(msgPackTree);
            final long endWrite = System.currentTimeMillis();
            final long diff = endWrite - startWrite;

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
        System.out.println(String.format("Write avg execution time: %d ms", avgDiff));
        System.out.println(String.format("Write min execution time: %d ms", minDiff));
        System.out.println(String.format("Write max execution time: %d ms", maxDiff));
    }
}
