package io.zeebe.logstreams.rocksdb;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.ColumnFamilyHandle;

public class ZbIteratorTest {
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
    final ZbIterator iterator;

    // when
    dbRule.put(handle, data);
    wrappedIterator = db.newIterator(handle);
    iterator = new ZbIterator(wrappedIterator);
    wrappedIterator.seekToFirst();

    // then
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo(data[0]);
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo(data[1]);
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo(data[2]);
    assertThat(iterator.hasNext()).isFalse();
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  public void shouldBeControllableFromOutsideUsingWrappedIterator() {
    // given
    final ZbRocksEntry[] data =
        new ZbRocksEntry[] {
          new ZbRocksEntry(wrapString("1"), wrapString("1")),
          new ZbRocksEntry(wrapString("2"), wrapString("2")),
          new ZbRocksEntry(wrapString("3"), wrapString("3"))
        };
    final ZbRocksIterator wrappedIterator;
    final ZbIterator iterator;

    // when
    dbRule.put(handle, data);
    wrappedIterator = db.newIterator(handle);
    iterator = new ZbIterator(wrappedIterator);

    // then
    assertThat(iterator.hasNext()).isFalse();

    wrappedIterator.seek(data[1].getKey());
    assertThat(iterator.next()).isEqualTo(data[1]);
    assertThat(iterator.hasNext()).isTrue();

    wrappedIterator.seekToFirst();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo(data[0]);
    assertThat(iterator.hasNext()).isTrue();

    wrappedIterator.seekToLast();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isEqualTo(data[2]);
    assertThat(iterator.hasNext()).isFalse();
  }
}
