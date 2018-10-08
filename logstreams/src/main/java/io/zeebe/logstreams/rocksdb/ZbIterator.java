package io.zeebe.logstreams.rocksdb;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Predicate;

public class ZbIterator extends AutoCloseableManager
    implements Iterator<ZbRocksEntry>, Iterable<ZbRocksEntry> {
  protected final ZbRocksIterator rocksIterator;

  protected Predicate<? super ZbRocksEntry> predicate;

  public ZbIterator(ZbRocksIterator iterator) {
    rocksIterator = iterator;
  }

  public ZbIterator(ZbRocksIterator rocksIterator, List<AutoCloseable> closeables) {
    super(closeables);
    this.rocksIterator = rocksIterator;
  }

  @Override
  public Iterator<ZbRocksEntry> iterator() {
    return this;
  }

  @Override
  public boolean hasNext() {
    if (predicate != null) {
      findNextAcceptedEntry();
    }

    return rocksIterator.isValid();
  }

  @Override
  public ZbRocksEntry next() {
    if (!rocksIterator.isValid()) {
      throw new NoSuchElementException();
    }

    final ZbRocksEntry entry =
        new ZbRocksEntry(rocksIterator.keyBuffer(), rocksIterator.valueBuffer());
    rocksIterator.next();

    return entry;
  }

  @Override
  public Spliterator<ZbRocksEntry> spliterator() {
    return new ZbSpliterator(rocksIterator, 0, managedCloseables);
  }

  public void setPredicate(Predicate<? super ZbRocksEntry> predicate) {
    this.predicate = predicate;
  }

  private void findNextAcceptedEntry() {
    while (rocksIterator.isValid()) {
      final ZbRocksEntry entry =
          new ZbRocksEntry(rocksIterator.keyBuffer(), rocksIterator.valueBuffer());

      if (predicate.test(entry)) {
        break;
      }

      rocksIterator.next();
    }
  }
}
