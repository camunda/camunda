package io.zeebe.logstreams.rocksdb;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.ColumnFamilyHandle;

public class ZbSpliteratorTest {
  private final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private final ZbRocksDbRule dbRule = new ZbRocksDbRule(temporaryFolder);

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(temporaryFolder).around(dbRule);

  private ZbRocksDb db;
  private ColumnFamilyHandle handle;

  @Before
  public void setup() {
    db = dbRule.getDb();
    handle = db.getDefaultColumnFamily();
  }

  @Test
  public void shouldIterateOverAllEntries() {
    // given
    final ZbRocksEntry[] data =
        new ZbRocksEntry[] {
          new ZbRocksEntry(wrapString("1"), wrapString("1")),
          new ZbRocksEntry(wrapString("2"), wrapString("2")),
          new ZbRocksEntry(wrapString("3"), wrapString("3"))
        };
    final ZbRocksIterator wrappedIterator;
    final ZbSpliterator spliterator;
    final ZbRocksEntry entry = new ZbRocksEntry();

    // when
    dbRule.put(handle, data);
    wrappedIterator = db.newIterator(handle);
    spliterator = new ZbSpliterator(wrappedIterator, 3);
    wrappedIterator.seekToFirst();

    // then
    assertThat(spliterator.tryAdvance(entry::wrap)).isTrue();
    assertThat(entry).isEqualTo(data[0]);
    assertThat(spliterator.tryAdvance(entry::wrap)).isTrue();
    assertThat(entry).isEqualTo(data[1]);
    assertThat(spliterator.tryAdvance(entry::wrap)).isTrue();
    assertThat(entry).isEqualTo(data[2]);
    assertThat(spliterator.tryAdvance(entry::wrap)).isFalse();
    assertThat(entry).isEqualTo(data[2]);
  }

  @Test
  public void shouldNeverSplit() {
    // given
    final ZbRocksEntry[] data =
      new ZbRocksEntry[] {
        new ZbRocksEntry(wrapString("1"), wrapString("1")),
        new ZbRocksEntry(wrapString("2"), wrapString("2")),
        new ZbRocksEntry(wrapString("3"), wrapString("3"))
      };
    final ZbRocksIterator wrappedIterator;
    final ZbSpliterator spliterator;
    final ZbRocksEntry entry = new ZbRocksEntry();

    // when
    dbRule.put(handle, data);
    wrappedIterator = db.newIterator(handle);
    spliterator = new ZbSpliterator(wrappedIterator, 3);
    wrappedIterator.seekToFirst();

    // then
    assertThat(spliterator.tryAdvance(entry::wrap)).isTrue();
    assertThat(spliterator.trySplit()).isNull();
    assertThat(spliterator.tryAdvance(entry::wrap)).isTrue();
    assertThat(spliterator.trySplit()).isNull();
    assertThat(spliterator.tryAdvance(entry::wrap)).isTrue();
    assertThat(spliterator.trySplit()).isNull();
    assertThat(spliterator.tryAdvance(entry::wrap)).isFalse();
    assertThat(spliterator.trySplit()).isNull();
  }
}
