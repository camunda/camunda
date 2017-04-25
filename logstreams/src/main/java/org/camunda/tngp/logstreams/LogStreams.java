package org.camunda.tngp.logstreams;

import org.agrona.DirectBuffer;
import org.camunda.tngp.logstreams.fs.FsLogStreamBuilder;
import org.camunda.tngp.logstreams.fs.FsSnapshotStorageBuilder;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorBuilder;

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
