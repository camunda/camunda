package io.zeebe.logstreams.storage.atomix;

import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.protocols.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.atomix.storage.journal.Indexed;
import io.zeebe.logstreams.spi.LogStorage;

public class AtomixAppendListenerAdapter implements AppendListener {
  private final LogStorage.AppendListener delegate;

  public AtomixAppendListenerAdapter(final LogStorage.AppendListener delegate) {
    this.delegate = delegate;
  }

  @Override
  public void onWrite(final Indexed<ZeebeEntry> indexed) {
    delegate.onWrite(indexed.index());
  }

  @Override
  public void onWriteError(final Throwable error) {
    delegate.onWriteError(error);
  }

  @Override
  public void onCommit(final Indexed<ZeebeEntry> indexed) {
    delegate.onCommit(indexed.index());
  }

  @Override
  public void onCommitError(final Indexed<ZeebeEntry> indexed, final Throwable error) {
    delegate.onCommitError(indexed.index(), error);
  }
}
