package io.zeebe.journal.file.record;

import org.agrona.DirectBuffer;

public interface JournalIndexedRecord {

  long index();

  long asqn();

  DirectBuffer data();
}
