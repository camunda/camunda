package io.zeebe.logstreams.rocksdb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.agrona.CloseHelper;

public class AutoCloseableManager implements AutoCloseable {
  protected final List<AutoCloseable> managedCloseables;

  public AutoCloseableManager() {
    managedCloseables = new ArrayList<>();
  }

  public AutoCloseableManager(List<AutoCloseable> closeables) {
    this.managedCloseables = closeables;
  }

  public void addCloseable(AutoCloseable... closeables) {
    this.managedCloseables.addAll(Arrays.asList(closeables));
  }

  @Override
  public void close() {
    managedCloseables.forEach(CloseHelper::quietClose);
  }
}
