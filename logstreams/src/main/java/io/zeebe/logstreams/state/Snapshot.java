package io.zeebe.logstreams.state;

import java.nio.file.Path;

public interface Snapshot {
  long getPosition();

  Path getPath();
}
