/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.agrona.UnsafeApi;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

/** Adapted from Agrona's {@link ManyToOneConcurrentLinkedQueue}. */
@SuppressWarnings("restriction")
public final class ActorTaskQueue extends ActorTaskQueueHead {
  // unlinking stale nodes writes to a shared node; only do it when it saves a long walk
  static final int MIN_STALE_NODES_TO_SKIP = 16;

  @SuppressWarnings("unused")
  protected long p31, p32, p33, p34, p35, p36, p37, p38, p39, p40, p41, p42, p43, p44, p45;
  private final ActorTaskQueueNode empty = new ActorTaskQueueNode();

  public ActorTaskQueue() {
    headOrdered(empty);
    UnsafeApi.putReferenceRelease(this, TAIL_OFFSET, empty);
  }

  /** appends a task at the end (tail) of the list */
  public void append(final ActorTask task) {
    // TODO: make garbage free again
    final ActorTaskQueueNode tail = new ActorTaskQueueNode();
    tail.task = task;
    tail.stateCount = task.getStateCount();
    final ActorTaskQueueNode previousTail = swapTail(tail);
    previousTail.nextOrdered(tail);
    tail.prevOrdered(previousTail);
  }

  /**
   * Attempts to steal a task from this list. The operation does not actually remove the task.
   * Instead, it modifies the task's state.
   *
   * <p>Nodes whose task was already claimed stay in the list until the owner pops them. A claim
   * only ever increments the task's state count, so such a node can never be claimed again. To
   * avoid walking long runs of them on every attempt (e.g. while the owner is blocked and the list
   * keeps growing), such runs are unlinked from the {@code prev} chain, which is only used for
   * stealing. Short runs are left in place, so that the common case does not write to shared nodes.
   *
   * @return the actor which was stolen or null in case no actor is available
   */
  public ActorTask trySteal() {
    final ActorTaskQueueNode newest = tail;
    // A thief only ever rewrites the prev of the second-newest node: the tail may still be linked
    // by its producer. Stale nodes walked below it are counted, and if there are enough, its prev
    // is pointed at the node where the walk stops.
    ActorTaskQueueNode anchor = null;
    ActorTaskQueueNode anchorPrev = null;
    int stale = 0;

    ActorTaskQueueNode node = newest;
    while (node != null && node != empty) {
      final ActorTask task = node.task;
      if (task.claim(node.stateCount)) {
        skipStaleNodes(anchor, anchorPrev, node, stale);
        return task;
      }

      final ActorTaskQueueNode older = node.prev;
      if (older == null) {
        // still being appended, or popped after the walk read the link to it: nothing older to
        // steal
        skipStaleNodes(anchor, anchorPrev, node, stale);
        return null;
      }

      if (anchor != null) {
        stale++;
      } else if (node != newest) {
        anchor = node;
        anchorPrev = older;
      }
      node = older;
    }

    skipStaleNodes(anchor, anchorPrev, node, stale);
    return null;
  }

  /**
   * Points {@code anchor} past {@code stale} claimed nodes to {@code next}, if that saves a long
   * walk. Does nothing if its prev changed since the walk read it: another thief may have unlinked
   * further in the meantime, and overwriting that would bring back the nodes it skipped.
   */
  static void skipStaleNodes(
      final ActorTaskQueueNode anchor,
      final ActorTaskQueueNode anchorPrev,
      final ActorTaskQueueNode next,
      final int stale) {
    if (anchor != null && stale >= MIN_STALE_NODES_TO_SKIP && next != null) {
      UnsafeApi.compareAndSetReference(anchor, PREV_OFFSET, anchorPrev, next);
    }
  }

  public ActorTask pop() {
    ActorTask task = null;
    ActorTaskQueueNode node = null;

    do {
      node = poll();

      if (node != empty && node != null) {
        final ActorTask t = node.task;
        if (t.claim(node.stateCount)) {
          task = t;
          break;
        }
      }
    } while (node != null && node != empty);

    return task;
  }

  private ActorTaskQueueNode poll() {
    ActorTaskQueueNode value = null;
    final ActorTaskQueueNode head = this.head;
    ActorTaskQueueNode next = head.next;

    if (null != next) {
      value = next;
      head.prevOrdered(null);
      head.nextOrdered(null);

      if (null == next.next) {
        final ActorTaskQueueNode tail = this.tail;
        if (tail == next && casTail(tail, empty)) {
          next = empty;
        }
      }

      headOrdered(next);
    }

    return value;
  }

  public ActorTaskQueueNode peek() {
    final ActorTaskQueueNode next = head.next;
    return next == empty ? null : next;
  }

  public boolean isEmpty() {
    return head == tail;
  }

  private void headOrdered(final ActorTaskQueueNode head) {
    UnsafeApi.putReferenceRelease(this, HEAD_OFFSET, head);
  }

  private ActorTaskQueueNode swapTail(final ActorTaskQueueNode newTail) {
    return (ActorTaskQueueNode) UnsafeApi.getAndSetReference(this, TAIL_OFFSET, newTail);
  }

  private boolean casTail(
      final ActorTaskQueueNode expectedNode, final ActorTaskQueueNode updateNode) {
    return UnsafeApi.compareAndSetReference(this, TAIL_OFFSET, expectedNode, updateNode);
  }
}

@SuppressWarnings("restriction")
class ActorTaskQueueNode {
  protected static final long PREV_OFFSET;
  protected static final long NEXT_OFFSET;

  static {
    try {
      PREV_OFFSET = UnsafeApi.objectFieldOffset(ActorTaskQueueNode.class.getDeclaredField("prev"));
      NEXT_OFFSET = UnsafeApi.objectFieldOffset(ActorTaskQueueNode.class.getDeclaredField("next"));
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @SuppressFBWarnings(
      value = "UWF_UNWRITTEN_FIELD",
      justification = "Written using UnsafeApi (see NEXT_OFFSET usage)")
  volatile ActorTaskQueueNode next;

  @SuppressFBWarnings(
      value = "UWF_UNWRITTEN_FIELD",
      justification = "Written using UnsafeApi (see PREV_OFFSET usage)")
  volatile ActorTaskQueueNode prev;

  long stateCount;
  ActorTask task;

  void nextOrdered(final ActorTaskQueueNode t) {
    assert t != this;
    UnsafeApi.putReferenceRelease(this, NEXT_OFFSET, t);
  }

  void prevOrdered(final ActorTaskQueueNode t) {
    assert t != this;
    UnsafeApi.putReferenceVolatile(this, PREV_OFFSET, t);
  }

  public void setTask(final ActorTask task) {
    this.task = task;
  }
}

@SuppressWarnings("restriction")
class ActorTaskQueuePadding1 {
  protected static final long HEAD_OFFSET;
  protected static final long TAIL_OFFSET;
  protected static final long PREV_OFFSET;
  protected static final long NEXT_OFFSET;
  protected static final long STATE_COUNT_OFFSET;

  static {
    try {
      HEAD_OFFSET = UnsafeApi.objectFieldOffset(ActorTaskQueueHead.class.getDeclaredField("head"));
      TAIL_OFFSET = UnsafeApi.objectFieldOffset(ActorTaskQueueTail.class.getDeclaredField("tail"));
      PREV_OFFSET = UnsafeApi.objectFieldOffset(ActorTaskQueueNode.class.getDeclaredField("prev"));
      NEXT_OFFSET = UnsafeApi.objectFieldOffset(ActorTaskQueueNode.class.getDeclaredField("next"));
      STATE_COUNT_OFFSET =
          UnsafeApi.objectFieldOffset(ActorTask.class.getDeclaredField("stateCount"));
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @SuppressWarnings("unused")
  protected long p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15;
}

/** Value for the tail that is expected to be padded. */
class ActorTaskQueueTail extends ActorTaskQueuePadding1 {
  protected volatile ActorTaskQueueNode tail;
}

/** Pad out a cache line between the tail and the head to prevent false sharing. */
class ActorTaskQueuePadding2 extends ActorTaskQueueTail {
  @SuppressWarnings("unused")
  protected long p16, p17, p18, p19, p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p30;
}

/** Value for the head that is expected to be padded. */
class ActorTaskQueueHead extends ActorTaskQueuePadding2 {
  protected volatile ActorTaskQueueNode head;
}
