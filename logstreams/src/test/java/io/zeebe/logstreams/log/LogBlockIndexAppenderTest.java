package io.zeebe.logstreams.log;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.time.Duration;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.logstreams.impl.LogBlockIndexAppender;
import io.zeebe.logstreams.impl.LogEntryDescriptor;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.spi.ReadableSnapshot;
import io.zeebe.logstreams.util.LogStreamRule;
import io.zeebe.logstreams.util.LogStreamWriterRule;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public class LogBlockIndexAppenderTest
{
    private static final DirectBuffer EVENT_1 = wrapString("FOO");
    private static final DirectBuffer EVENT_2 = wrapString("BAR");

    private static final int EVENT_SIZE = EVENT_1.capacity();
    private static final int FRAGMENT_SIZE = DataFrameDescriptor.alignedFramedLength(LogEntryDescriptor.HEADER_BLOCK_LENGTH + EVENT_SIZE);
    private static final int INDEX_BLOCK_SIZE = 2 * FRAGMENT_SIZE;

    private static final Duration SNAPSHOT_INTERVAL = Duration.ofSeconds(1);

    private TemporaryFolder temporaryFolder = new TemporaryFolder();

    private LogStreamRule logStreamRule = new LogStreamRule(temporaryFolder, b -> b
                                                            .indexBlockSize(INDEX_BLOCK_SIZE)
                                                            .readBlockSize(FRAGMENT_SIZE)
                                                            .snapshotPeriod(SNAPSHOT_INTERVAL));

    private LogStreamWriterRule writer = new LogStreamWriterRule(logStreamRule);

    @Rule
    public RuleChain ruleChain =
        RuleChain.outerRule(temporaryFolder)
                 .around(logStreamRule)
                 .around(writer);

    private LogBlockIndexAppender indexAppender;
    private LogBlockIndex blockIndex;
    private LogStorage logStorage;

    @Before
    public void setup()
    {
        final LogStream logStream = logStreamRule.getLogStream();
        blockIndex = logStream.getLogBlockIndex();
        logStorage = logStream.getLogStorage();
        indexAppender = logStream.getLogBlockIndexController();
    }

    @Test
    public void shouldAppendBlockWhenIndexBlockSizeIsReached()
    {
        final long firstEventPosition = writer.writeEvent(EVENT_1, true);
        final long secondEventPosition = writer.writeEvent(EVENT_2, true);

        waitUntil(() -> blockIndex.size() == 1);

        // verify that both events are in the same block
        assertThat(blockIndex.lookupBlockPosition(firstEventPosition)).isEqualTo(blockIndex.lookupBlockPosition(secondEventPosition));

        assertThat(blockIndex.lookupBlockAddress(firstEventPosition)).isEqualTo(blockIndex.lookupBlockAddress(secondEventPosition));
    }

    @Test
    public void shouldAppendBlockWithPositionAndAddressOfFirstEventInTheBlock()
    {
        final long firstEventPosition = writer.writeEvent(EVENT_1, true);
        writer.writeEvent(EVENT_2, true);

        waitUntil(() -> blockIndex.size() == 1);

        final long indexPosition = blockIndex.getLogPosition(0);
        final long indexAddress = blockIndex.getAddress(0);

        assertThat(indexPosition).isEqualTo(firstEventPosition);

        assertThat(readEventAtAddress(indexAddress)).isEqualTo(EVENT_1);
    }

    @Test
    public void shouldAppendMultipleBlocks()
    {
        // 4 blocks
        writer.writeEvents(8, EVENT_1, true);
        // plus one more
        final long eventPositionOfLastBlock = writer.writeEvent(EVENT_2, true);
        writer.writeEvent(EVENT_1, true);

        waitUntil(() -> blockIndex.size() == 5);

        final long indexPosition = blockIndex.getLogPosition(4);
        final long indexAddress = blockIndex.getAddress(4);

        assertThat(indexPosition).isEqualTo(eventPositionOfLastBlock);

        assertThat(readEventAtAddress(indexAddress)).isEqualTo(EVENT_2);
    }

    @Test
    public void shouldWriteSnapshot()
    {
        writer.writeEvents(2, EVENT_1, true);

        final long eventPositionOfLastBlock = writer.writeEvent(EVENT_1, true);
        writer.writeEvent(EVENT_2, true);

        waitUntil(() -> blockIndex.size() == 2);

        logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);
        waitUntil(() -> getLatestSnapshot() != null);

        final long snapshotPosition = getLatestSnapshot().getPosition();
        assertThat(snapshotPosition).isEqualTo(eventPositionOfLastBlock);
    }

    @Test
    public void shouldWriteSnapshotWithPositionOfLastBlock()
    {
        final long eventPositionOfLastBlock = writer.writeEvent(EVENT_1, true);
        writer.writeEvent(EVENT_1, true);

        writer.writeEvent(EVENT_2, true);

        waitUntil(() -> blockIndex.size() == 1);

        logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);
        waitUntil(() -> getLatestSnapshot() != null);

        final long snapshotPosition = getLatestSnapshot().getPosition();
        assertThat(snapshotPosition).isEqualTo(eventPositionOfLastBlock);
    }

    @Test
    public void shouldRecoverBlockIndexFromSnapshot()
    {
        // given
        writer.writeEvents(2, EVENT_1, true);
        writer.writeEvents(2, EVENT_2, true);

        waitUntil(() -> blockIndex.size() == 2);

        logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);
        waitUntil(() -> getLatestSnapshot() != null);

        indexAppender.close();
        blockIndex.reset();

        assertThat(blockIndex.size()).isEqualTo(0);

        // when
        indexAppender.open();

        // then
        assertThat(blockIndex.size()).isEqualTo(2);
    }

    @Test
    public void shouldAppendBlockAfterRecover()
    {
        // given
        writer.writeEvent(EVENT_1, true);
        writer.writeEvent(EVENT_1, true);

        waitUntil(() -> blockIndex.size() == 1);

        logStreamRule.getClock().addTime(SNAPSHOT_INTERVAL);
        waitUntil(() -> getLatestSnapshot() != null);

        indexAppender.close();
        indexAppender.open();

        assertThat(blockIndex.size()).isEqualTo(1);

        // when
        writer.writeEvents(2, EVENT_2, true);

        // then
        waitUntil(() -> blockIndex.size() == 2);
    }

    @Test
    public void shouldIncreateReadBuffer()
    {
        final DirectBuffer event = new UnsafeBuffer(new byte[INDEX_BLOCK_SIZE]);

        // when
        writer.writeEvent(event, true);

        // then buffer is increased and block is appended
        waitUntil(() -> blockIndex.size() == 1);
    }

    private UnsafeBuffer readEventAtAddress(final long indexAddress)
    {
        final ByteBuffer buffer = ByteBuffer.allocate(FRAGMENT_SIZE);
        logStorage.read(buffer, indexAddress);

        final int headerLength = DataFrameDescriptor.HEADER_LENGTH + LogEntryDescriptor.HEADER_BLOCK_LENGTH;
        return new UnsafeBuffer(buffer, headerLength, EVENT_SIZE);
    }

    private ReadableSnapshot getLatestSnapshot()
    {
        try
        {
            final String snapshotName = logStreamRule.getLogStream().getLogBlockIndexController().getName();
            return logStreamRule.getSnapshotStorage().getLastSnapshot(snapshotName);
        }
        catch (Exception e)
        {
            fail("Fail to read snapshot", e);
            return null;
        }
    }

}
