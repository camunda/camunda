package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.zeebe.broker.clustering.atomix.storage.AtomixRecordEntrySupplier;
import io.zeebe.logstreams.storage.atomix.AtomixLogStorageReader;
import java.util.Optional;

public class AtomixRecordEntrySupplierImpl implements AtomixRecordEntrySupplier {
  private final AtomixLogStorageReader reader;

  public AtomixRecordEntrySupplierImpl(final AtomixLogStorageReader reader) {
    this.reader = reader;
  }

  @Override
  public Optional<Indexed<ZeebeEntry>> getIndexedEntry(final long position) {
    final var index = reader.lookUpApproximateAddress(position, null);
    // since it already looked it up, it should return the current entry without doing anything
    return reader.findEntry(index);
  }

  @Override
  public void close() {
    reader.close();
  }
}
