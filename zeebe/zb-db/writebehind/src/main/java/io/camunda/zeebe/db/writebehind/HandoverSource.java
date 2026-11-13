/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

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
final class HandoverSource<K extends Comparable<K>, V, T> implements CommittedSource<K, V, T> {

  private final IndexSource<K, V, T> index;
  private final WriteBehindStore<K, V, T> store;
  private final KeyRange<K> range;
  private final K bound;

  private boolean forward = true;
  private @Nullable ViewSource<K, V, T> view;

  /**
   * @param range the range of the scan, which restricts the view
   * @param bound the first key that the window doesn't cover
   */
  HandoverSource(
      final IndexSource<K, V, T> index,
      final WriteBehindStore<K, V, T> store,
      final KeyRange<K> range,
      final K bound) {
    this.index = index;
    this.store = store;
    this.range = range;
    this.bound = bound;
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
  public boolean readValueUnlessStale(final T target) {
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

  private CommittedSource<K, V, T> active() {
    final var opened = view;
    return opened != null ? opened : index;
  }

  private void handOverIfExhausted() {
    if (view != null || index.isStale() || index.isValid()) {
      return;
    }
    final var opened = new ViewSource<>(store.backend(), store.openView(range));
    view = opened;
    store.metrics().keyIndexHandovers.increment();
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
