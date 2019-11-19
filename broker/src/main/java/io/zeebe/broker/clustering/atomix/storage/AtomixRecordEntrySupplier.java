package io.zeebe.broker.clustering.atomix.storage;

import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import io.zeebe.protocol.record.Record;
import java.util.Optional;

/**
 * Implementations of this interface should provide the correct {@link Indexed<ZeebeEntry>} when
 * given a {@link Record#getPosition()}
 */
@FunctionalInterface
public interface AtomixRecordEntrySupplier extends AutoCloseable {
  Optional<Indexed<ZeebeEntry>> getIndexedEntry(final long position);

  @Override
  default void close() {

  }
}
