package io.camunda.zeebe.dynamic.nodeid.fs;

import io.camunda.zeebe.dynamic.nodeid.NodeInstance;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A data directory provider that creates a versioned data directory based on nodeId and nodeVersion
 * under a shared root. Unlike {@link VersionedNodeIdBasedDataDirectoryProvider}, this provider does
 * not copy from previous versions or initialize the directory.
 */
public class UnInitializedVersionedNodeIdBasedDataDirectoryProvider
    implements DataDirectoryProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(UnInitializedVersionedNodeIdBasedDataDirectoryProvider.class);

  private static final String NODE_DIRECTORY_PREFIX = "node-";

  private final NodeInstance nodeInstance;

  public UnInitializedVersionedNodeIdBasedDataDirectoryProvider(final NodeInstance nodeInstance) {
    this.nodeInstance = nodeInstance;
  }

  @Override
  public CompletableFuture<Path> initialize(final Path rootDataDirectory) {
    if (nodeInstance == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Node instance is not available"));
    }

    try {
      final var nodeId = nodeInstance.id();
      final var nodeVersion = nodeInstance.version().version();

      final var nodeDirectory = rootDataDirectory.resolve(NODE_DIRECTORY_PREFIX + nodeId);
      final var dataDirectory = nodeDirectory.resolve(String.valueOf(nodeVersion));

      if (Files.exists(dataDirectory)) {
        LOG.info("Data directory {} already exists", dataDirectory);
        return CompletableFuture.completedFuture(dataDirectory);
      }

      LOG.info(
          "Creating data directory {} for node {} version {}", dataDirectory, nodeId, nodeVersion);
      Files.createDirectories(dataDirectory);

      return CompletableFuture.completedFuture(dataDirectory);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }
}
