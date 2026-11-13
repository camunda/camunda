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
    // note: StealCursor compares nodes by identity, so nodes must not be reused while reachable
    final ActorTaskQueueNode tail = new ActorTaskQueueNode();
    tail.task = task;
    tail.stateCount = task.getStateCount();
    final ActorTaskQueueNode previousTail = swapTail(tail);
    previousTail.nextOrdered(tail);
    tail.prevOrdered(previousTail);
  }

  /**
   * Attempts to steal a task from this list, without remembering anything between attempts.
   *
   * @return the actor which was stolen or null in case no actor is available
   * @see #trySteal(StealCursor)
   */
  public ActorTask trySteal() {
    return trySteal(new StealCursor());
  }

  /**
   * Attempts to steal a task from this list. The operation does not actually remove the task.
   * Instead, it modifies the task's state.
   *
   * <p>Nodes whose task was already claimed stay in the list until the owner pops them. A claim
   * only ever increments the task's state count, so such a node can never be claimed again. While
   * the owner is blocked and the list keeps growing, walking those nodes on every attempt is
   * expensive. The cursor remembers a historical run and a recent prefix of stale nodes. A
   * successful steal above the historical run advances the prefix, so draining a burst of live
   * tasks does not repeatedly scan the tasks just stolen. A walk connecting the two runs merges
   * them. The list itself is only read.
   *
   * @param cursor remembers the walk of the previous attempt; must only be used by one thread and
   *     for this queue
   * @return the actor which was stolen or null in case no actor is available
   */
  public ActorTask trySteal(final StealCursor cursor) {
    final ActorTaskQueueNode skipFrom = cursor.newest;
    final ActorTaskQueueNode skipTo = cursor.oldest;
    final ActorTaskQueueNode prefixFrom = cursor.prefixNewest;
    final ActorTaskQueueNode prefixTo = cursor.prefixOldest;
    // every node this walk visits or jumps over is stale or claimed by it, and they are contiguous
    ActorTaskQueueNode newest = null;
    ActorTaskQueueNode oldest = null;
    // whether this walk reached the remembered run, i.e. its nodes connect to that run
    boolean reachedRun = skipFrom == null;
    ActorTaskQueueNode node = tail;

    while (node != null && node != empty) {
      if (node == prefixFrom) {
        if (newest == null) {
          newest = prefixFrom;
        }
        oldest = prefixTo;
        node = prefixTo.prev;
        continue;
      }

      if (node == skipFrom) {
        reachedRun = true;
        if (newest == null) {
          newest = skipFrom;
        }
        oldest = skipTo;
        node = skipTo.prev;
        continue;
      }

      final ActorTask task = node.task;
      final boolean claimed = task.claim(node.stateCount);
      if (newest == null) {
        newest = node;
      }
      oldest = node;
      if (claimed) {
        if (reachedRun) {
          cursor.remember(newest, oldest);
        } else {
          // Keep the historical run across an unchecked gap, but remember this stale prefix too.
          cursor.prefixNewest = newest;
          cursor.prefixOldest = oldest;
        }
        return task;
      }

      node = node.prev;
    }

    // every node down to the end of the walk is stale; this replaces a run the walk did not reach,
    // e.g. one the owner popped meanwhile
    cursor.remember(newest, oldest);
    return null;
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

  /**
   * Remembers, for one thief and one queue, two runs of stale nodes following {@code prev}: a
   * historical run and the prefix walked by a successful steal above it. Each run contains only
   * stale or just-claimed nodes; the gap between them may contain live tasks and must be walked.
   * Thieves never change the list and nodes are never reused. Confined to the thief's thread.
   *
   * <p>Only four endpoints are retained, even across nested bursts. If a steal stops above the
   * recent prefix, it replaces that prefix; the displaced intermediate run may be walked again. A
   * failed steal or a walk reaching the historical run merges the visited nodes into one run.
   * Endpoints can retain popped tasks until a later attempt replaces them.
   */
  public static final class StealCursor {
    // package-private for tests
    ActorTaskQueueNode newest;
    ActorTaskQueueNode oldest;
    private ActorTaskQueueNode prefixNewest;
    private ActorTaskQueueNode prefixOldest;

    private void remember(final ActorTaskQueueNode newest, final ActorTaskQueueNode oldest) {
      if (this.newest != newest || this.oldest != oldest) {
        this.newest = newest;
        this.oldest = oldest;
      }
      prefixNewest = null;
      prefixOldest = null;
    }
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
