package io.zeebe.logstreams;

import org.agrona.DirectBuffer;
import io.zeebe.logstreams.fs.FsLogStreamBuilder;
import io.zeebe.logstreams.fs.FsSnapshotStorageBuilder;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorBuilder;

public class LogStreams
{
    public static FsLogStreamBuilder createFsLogStream(final DirectBuffer topicName, final int partitionId)
    {
        return new FsLogStreamBuilder(topicName, partitionId);
    }

    public static FsSnapshotStorageBuilder createFsSnapshotStore(String rootPath)
    {
        return new FsSnapshotStorageBuilder(rootPath);
    }

    public static StreamProcessorBuilder createStreamProcessor(String name, int id, StreamProcessor streamProcessor)
    {
        return new StreamProcessorBuilder(id, name, streamProcessor);
    }

}
