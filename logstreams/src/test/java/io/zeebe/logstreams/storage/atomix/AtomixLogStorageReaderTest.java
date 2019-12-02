package io.zeebe.logstreams.storage.atomix;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import io.atomix.protocols.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.protocols.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.atomix.storage.journal.Indexed;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.util.AtomixLogStorageRule;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomixLogStorageReaderTest {
  private static final ByteBuffer DATA = ByteBuffer.allocate(4).putInt(0, 1);
  private static final Logger LOGGER = LoggerFactory.getLogger(AtomixLogStorageReaderTest.class);

  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final AtomixLogStorageRule storageRule = new AtomixLogStorageRule(temporaryFolder);

  @Rule public final RuleChain chain = RuleChain.outerRule(temporaryFolder).around(storageRule);

  @Test
  public void shouldLookUpAddress() {
    // given
    final var reader = storageRule.get().newReader();
    final var entries = List.of(append(1, 4, DATA), append(5, 8, DATA), append(9, 10, DATA));

    // when - then
    IntStream.range(1, 4)
        .forEach(
            i -> assertThat(reader.lookUpApproximateAddress(i)).isEqualTo(entries.get(0).index()));
    IntStream.range(5, 8)
        .forEach(
            i -> assertThat(reader.lookUpApproximateAddress(i)).isEqualTo(entries.get(1).index()));
  }

  @Test
  public void shouldReturnInvalidOnEmptyLookUp() {
    // given
    final var reader = storageRule.get().newReader();

    // when - then
    assertThat(reader.lookUpApproximateAddress(1)).isEqualTo(LogStorage.OP_RESULT_INVALID_ADDR);
  }

  @Test
  public void shouldReturnNoDataOnEmptyRead() {
    // given
    final var reader = storageRule.get().newReader();

    // when - then
    assertThat(reader.read(ByteBuffer.allocate(1), 1)).isEqualTo(LogStorage.OP_RESULT_NO_DATA);
  }

  @Test
  public void shouldReadEntry() {
    // given
    final var reader = storageRule.get().newReader();
    final var entries =
        List.of(
            append(1, 4, ByteBuffer.allocate(4).putInt(0, 1)),
            append(5, 8, ByteBuffer.allocate(4).putInt(0, 2)),
            append(9, 10, ByteBuffer.allocate(4).putInt(0, 3)));
    final var buffer = ByteBuffer.allocate(4);

    // when - then
    IntStream.range(1, 4)
        .forEach(
            i -> {
              assertThat(reader.read(buffer.clear(), entries.get(0).index()))
                  .isEqualTo(entries.get(1).index());
              assertThat(buffer.getInt(0)).isEqualTo(1);
            });
    IntStream.range(5, 8)
        .forEach(
            i -> {
              assertThat(reader.read(buffer.clear(), entries.get(1).index()))
                  .isEqualTo(entries.get(2).index());
              assertThat(buffer.getInt(0)).isEqualTo(2);
            });
  }

  @Test
  public void shouldLookUpEntryWithGaps() {
    // given
    final var reader = storageRule.get().newReader();
    final var buffer = ByteBuffer.allocate(4);

    // when
    final var first = append(1, 4, ByteBuffer.allocate(4).putInt(0, 1));
    final var second =
        storageRule
            .getRaftLog()
            .writer()
            .append(new ConfigurationEntry(1, System.currentTimeMillis(), Collections.emptyList()));
    final var third = append(5, 8, ByteBuffer.allocate(4).putInt(0, 2));

    // then
    assertThat(reader.lookUpApproximateAddress(3)).isEqualTo(first.index());
    assertThat(reader.lookUpApproximateAddress(6)).isEqualTo(third.index());

    // this does not point to the next real ZeebeEntry, but is just an approximate
    assertThat(reader.read(buffer.clear(), first.index())).isEqualTo(second.index());
    assertThat(buffer.getInt(0)).isEqualTo(1);

    // reading the second entry (a non ZeebeEntry) should find the next correct Zeebe entry
    assertThat(reader.read(buffer.clear(), second.index())).isEqualTo(third.index() + 1);
    assertThat(buffer.getInt(0)).isEqualTo(2);

    assertThat(reader.read(buffer.clear(), third.index())).isEqualTo(third.index() + 1);
    assertThat(buffer.getInt(0)).isEqualTo(2);
  }



  private Indexed<ZeebeEntry> append(
      final long lowestPosition, final long highestPosition, final ByteBuffer data) {
    final var future = new CompletableFuture<Indexed<ZeebeEntry>>();
    final var listener = new Listener(future);
    storageRule.appendEntry(lowestPosition, highestPosition, data, listener);
    return future.join();
  }

  private static final class Listener implements AppendListener {
    private final CompletableFuture<Indexed<ZeebeEntry>> future;

    private Listener(final CompletableFuture<Indexed<ZeebeEntry>> future) {
      this.future = future;
    }

    @Override
    public void onWrite(final Indexed<ZeebeEntry> indexed) {
      future.complete(indexed);
    }

    @Override
    public void onWriteError(final Throwable throwable) {
      future.completeExceptionally(throwable);
    }

    @Override
    public void onCommit(final Indexed<ZeebeEntry> indexed) {
      // do nothing
    }

    @Override
    public void onCommitError(final Indexed<ZeebeEntry> indexed, final Throwable throwable) {
      // do nothing
    }
  }
}
