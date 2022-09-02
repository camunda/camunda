# Backup Store Testkit

This module provides test interfaces that can be used to verify behavior of backup store
implementations.

## Usage

Let's say we want to implement a backup store that uses Google Cloud Storage (GCS):

```java
import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class GcsBackupStore implements BackupStore {
  @Override
  public CompletableFuture<Void> save(final Backup backup) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<BackupStatus> getStatus(final BackupIdentifier id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Void> delete(final BackupIdentifier id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<Backup> restore(final BackupIdentifier id, final Path targetFolder) {
    throw new UnsupportedOperationException();
  }

  @Override
  public CompletableFuture<BackupStatusCode> markFailed(final BackupIdentifier id,
      final String failureReason) {
    throw new UnsupportedOperationException();
  }
}
```

To test that this implementation behaves like Zeebe expects, we can add an integration test
that extends the testkit:

```java
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.testkit.BackupStoreTestKit;

public final class GcsBackupStoreIntegrationTest implements BackupStoreTestKit {
  private static final GcsBackupStore store = new GcsBackupStore();

  @Override
  public BackupStore getStore() {
    return store;
  }
}
```

The testkit requires a clean state for each test, so let's make sure that we use a new GCS bucket
every for every test:

```java
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.testkit.BackupStoreTestKit;
import org.junit.jupiter.api.BeforeEach;

public final class GcsBackupStoreIntegrationTest implements BackupStoreTestKit {

  private GcsBackupStore store;

  @BeforeEach
  void setupNewBackupStore() {
    final String randomBucketName = createNewTestBucket();
    store = GcsBackupStore(randomBucketName);
  }

  @Override
  public BackupStore getStore() {
    return null;
  }
}
```

As we implement `GcsBackupStore`, we can extend `GcsBackupStoreIntegrationTest` with additional tests
that are specific for GCS or our implementation.
