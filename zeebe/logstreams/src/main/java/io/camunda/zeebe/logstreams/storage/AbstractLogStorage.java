/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.storage;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Keeps the {@link CommitListener}s, {@link CommittedPositionListener}s and {@link
 * AppendedListener}s of a {@link LogStorage}, so that an implementation only has to decide when to
 * notify them.
 */
public abstract class AbstractLogStorage implements LogStorage {

  private final Set<CommitListener> commitListeners = new CopyOnWriteArraySet<>();
  private final Set<CommittedPositionListener> committedPositionListeners =
      new CopyOnWriteArraySet<>();
  private final Set<AppendedListener> appendedListeners = new CopyOnWriteArraySet<>();

  @Override
  public void addCommitListener(final CommitListener listener) {
    commitListeners.add(listener);
  }

  @Override
  public void removeCommitListener(final CommitListener listener) {
    commitListeners.remove(listener);
  }

  @Override
  public void addCommittedPositionListener(final CommittedPositionListener listener) {
    committedPositionListeners.add(listener);
  }

  @Override
  public void removeCommittedPositionListener(final CommittedPositionListener listener) {
    committedPositionListeners.remove(listener);
  }

  @Override
  public void addAppendedListener(final AppendedListener listener) {
    appendedListeners.add(listener);
  }

  @Override
  public void removeAppendedListener(final AppendedListener listener) {
    appendedListeners.remove(listener);
  }

  protected void notifyCommit() {
    commitListeners.forEach(CommitListener::onCommit);
  }

  protected void notifyCommittedPosition(final long highestPosition) {
    committedPositionListeners.forEach(listener -> listener.onCommittedPosition(highestPosition));
  }

  protected void notifyAppended(final long highestPosition) {
    appendedListeners.forEach(listener -> listener.onAppend(highestPosition));
  }
}
