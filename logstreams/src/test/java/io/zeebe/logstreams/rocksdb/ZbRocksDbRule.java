package io.zeebe.logstreams.rocksdb;

import java.util.ArrayList;
import java.util.List;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.Options;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

/**
 * Sets up and tears down a ZbRocksDb instance for a test.
 *
 * <p>Suggested to add useful debugging/testing methods here.
 */
public class ZbRocksDbRule extends ExternalResource {
  private final TemporaryFolder temporaryFolder;

  private Options options;
  private ZbRocksDb db;

  public ZbRocksDbRule(final TemporaryFolder temporaryFolder) {
    this.temporaryFolder = temporaryFolder;
  }

  @Override
  protected void before() throws Throwable {
    super.before();
    final String dbDirectory = temporaryFolder.newFolder().getAbsolutePath();
    options = new Options().setCreateIfMissing(true);
    db = ZbRocksDb.open(options, dbDirectory);
  }

  @Override
  protected void after() {
    super.after();
    if (db != null) {
      db.close();
    }
    if (options != null) {
      options.close();
    }
  }

  public ZbRocksDb getDb() {
    return db;
  }

  public List<ZbRocksEntry> list(final ColumnFamilyHandle handle) {
    final List<ZbRocksEntry> entries = new ArrayList<>();
    db.forEach(handle, (e) -> entries.add(new ZbRocksEntry(e)));

    return entries;
  }

  public void put(final ColumnFamilyHandle handle, ZbRocksEntry... entries) {
    try (final WriteOptions options = new WriteOptions();
        final ZbWriteBatch batch = new ZbWriteBatch()) {
      for (ZbRocksEntry entry : entries) {
        batch.put(handle, entry.getKey(), entry.getValue());
      }

      db.write(options, batch);
    } catch (final RocksDBException e) {
      throw new RuntimeException(e);
    }
  }
}
