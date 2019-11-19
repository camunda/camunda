package io.zeebe.broker.logstreams;

import java.nio.file.Path;

/**
 * Implementations are called whenever snapshots have been purged, indicating that the data they
 * required can now be removed from the logstream.
 *
 * <p>NOTE: this should be part of a larger refactor; the purpose of this interface is practically
 * obvious, but conceptually strange
 */
public interface DeletionService {

  /**
   * Called to notify that a snapshot has been re
   * @param position
   * @param directory
   */
  void delete(long position, Path directory);
}
