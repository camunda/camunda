package io.atomix.raft.zeebe.util;

import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.atomix.storage.journal.Indexed;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TestAppender implements AppendListener {
  private final BlockingQueue<Indexed<ZeebeEntry>> written;
  private final BlockingQueue<Indexed<ZeebeEntry>> committed;
  private final BlockingQueue<Throwable> errors;

  public TestAppender() {
    this.written = new LinkedBlockingQueue<>();
    this.committed = new LinkedBlockingQueue<>();
    this.errors = new LinkedBlockingQueue<>();
  }

  @Override
  public void onWrite(final Indexed<ZeebeEntry> indexed) {
    written.offer(indexed);
  }

  @Override
  public void onWriteError(final Throwable error) {
    errors.offer(error);
  }

  @Override
  public void onCommit(final Indexed<ZeebeEntry> indexed) {
    committed.offer(indexed);
  }

  @Override
  public void onCommitError(final Indexed<ZeebeEntry> indexed, final Throwable error) {
    errors.offer(error);
  }

  public Indexed<ZeebeEntry> append(
      final ZeebeLogAppender appender,
      final long lowest,
      final long highest,
      final ByteBuffer data) {
    appender.appendEntry(lowest, highest, data, this);
    return pollWritten();
  }

  public Indexed<ZeebeEntry> pollWritten() {
    return takeUnchecked(written);
  }

  public Indexed<ZeebeEntry> pollCommitted() {
    return takeUnchecked(committed);
  }

  public Throwable pollError() {
    return takeUnchecked(errors);
  }

  public List<Indexed<ZeebeEntry>> getWritten() {
    return new ArrayList<>(written);
  }

  public List<Indexed<ZeebeEntry>> getCommitted() {
    return new ArrayList<>(committed);
  }

  public List<Throwable> getErrors() {
    return new ArrayList<>(errors);
  }

  private <T> T takeUnchecked(final BlockingQueue<T> queue) {
    try {
      return queue.take();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
