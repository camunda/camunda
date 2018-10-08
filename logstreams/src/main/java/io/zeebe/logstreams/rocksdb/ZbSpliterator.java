package io.zeebe.logstreams.rocksdb;

import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * Spliterator implementation that cannot actually be split (as there is no use at the moment for
 * this).
 *
 * <p>Main reason to implement this is if you need for use with Stream API: >
 * StreamSupport.stream(mySpliterator, false); // false ensures it is sequential
 *
 * <p>Until Java 9 migration, streams have no filtering short-circuiting functions (e.g. takeWhile),
 * so it's recommended you use streams strictly if you're OK with iterating over the whole column
 * family.
 */
public class ZbSpliterator extends AutoCloseableManager implements Spliterator<ZbRocksEntry> {
  protected final ZbRocksIterator iterator;
  protected final long estimateSize;

  public ZbSpliterator(ZbRocksIterator iterator, long estimateSize) {
    this.iterator = iterator;
    this.estimateSize = estimateSize;
  }

  public ZbSpliterator(ZbRocksIterator iterator, long estimateSize, AutoCloseable... closeables) {
    super(closeables);
    this.iterator = iterator;
    this.estimateSize = estimateSize;
  }

  public ZbSpliterator(
      ZbRocksIterator iterator, long estimateSize, List<AutoCloseable> closeables) {
    super(closeables);
    this.iterator = iterator;
    this.estimateSize = estimateSize;
  }

  @Override
  public boolean tryAdvance(Consumer<? super ZbRocksEntry> action) {
    if (!iterator.isValid()) {
      return false;
    }

    action.accept(new ZbRocksEntry(iterator.keyBuffer(), iterator.valueBuffer()));
    iterator.next();

    return true;
  }

  @Override
  public Spliterator<ZbRocksEntry> trySplit() {
    return null;
  }

  @Override
  public long estimateSize() {
    return estimateSize;
  }

  @Override
  public int characteristics() {
    return Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.NONNULL;
  }
}
