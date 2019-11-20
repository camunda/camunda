package io.zeebe.logstreams.state;

import java.nio.file.Path;
import java.util.Comparator;

public interface Snapshot extends Comparable<Snapshot> {
  long getPosition();

  Path getPath();

  @Override
  default int compareTo(final Snapshot other) {
    return Comparator.comparingLong(Snapshot::getPosition).compare(this, other);
  }
}
