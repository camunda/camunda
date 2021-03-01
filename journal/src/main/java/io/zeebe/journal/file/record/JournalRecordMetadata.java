package io.zeebe.journal.file.record;

public interface JournalRecordMetadata {

  long checksum();

  long length();
}
