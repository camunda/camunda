/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Serves a scan from the window of a {@link KeyIndex}, and when the scan runs out of the window,
 * continues with a view at the window's bound. The window holds the keys before the bound in the
 * scan's direction and the view the keys from the bound on, so no key is repeated or skipped.
 *
 * <p>The hand-over is planned: the cursor doesn't see it, and {@link #isStale()} is only true while
 * the index source is the active one. The view is a fixed instant, so the source can't turn stale
 * after the hand-over.
 */
final class HandoverSource<K extends Comparable<K>, V> implements CommittedSource<K, V> {

  private final IndexSource<K, V> index;
  private final Supplier<CommittedSource<K, V>> viewOpener;
  private final K bound;
  private final Runnable onHandover;

  private boolean forward = true;
  private @Nullable CommittedSource<K, V> view;

  /**
   * @param viewOpener opens a view of the scan's prefix
   * @param bound the first key that the window doesn't cover
   */
  HandoverSource(
      final IndexSource<K, V> index,
      final Supplier<CommittedSource<K, V>> viewOpener,
      final K bound,
      final Runnable onHandover) {
    this.index = index;
    this.viewOpener = viewOpener;
    this.bound = bound;
    this.onHandover = onHandover;
  }

  @Override
  public void seek(final K key) {
    forward = true;
    closeView();
    index.seek(key);
    handOverIfExhausted();
  }

  @Override
  public void seekForPrev(final K key) {
    forward = false;
    closeView();
    index.seekForPrev(key);
    handOverIfExhausted();
  }

  @Override
  public boolean isValid() {
    return active().isValid();
  }

  @Override
  public K key() {
    return active().key();
  }

  @Override
  public void advance() {
    active().advance();
    handOverIfExhausted();
  }

  @Override
  public boolean readValueUnlessStale(final V target) {
    return active().readValueUnlessStale(target);
  }

  @Override
  public boolean isStale() {
    return view == null && index.isStale();
  }

  @Override
  public void close() {
    closeView();
    index.close();
  }

  private CommittedSource<K, V> active() {
    final var opened = view;
    return opened != null ? opened : index;
  }

  private void handOverIfExhausted() {
    if (view != null || index.isStale() || index.isValid()) {
      return;
    }
    final var opened = viewOpener.get();
    view = opened;
    onHandover.run();
    if (forward) {
      opened.seek(bound);
    } else {
      opened.seekForPrev(bound);
    }
  }

  private void closeView() {
    final var opened = view;
    if (opened != null) {
      opened.close();
      view = null;
    }
  }
}
