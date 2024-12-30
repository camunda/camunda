/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

/** Adapted from Agrona's {@link ManyToOneConcurrentLinkedQueue}. */
@SuppressWarnings("restriction")
public final class ActorTaskQueue extends ActorTaskQueueHead {
  @SuppressWarnings("unused")
  private long p31, p32, p33, p34, p35, p36, p37, p38, p39, p40, p41, p42, p43, p44, p45;
  private final ActorTaskQueueNode empty = new ActorTaskQueueNode();

  public ActorTaskQueue() {
    headOrdered(empty);
    TAIL_VAR.setRelease(this, empty);
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
   * @return the actor which was stolen or null in case no actor is available
   */
  public ActorTask trySteal() {
    ActorTaskQueueNode node = tail;

    while (node != null && node != empty) {
      final ActorTask task = node.task;
      if (task.claim(node.stateCount)) {
        return task;
      }

      node = node.prev;
    }

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
    HEAD_VAR.setRelease(this, head);
  }

  private ActorTaskQueueNode swapTail(final ActorTaskQueueNode newTail) {
    return (ActorTaskQueueNode) TAIL_VAR.getAndSet(this, newTail);
  }

  private boolean casTail(
      final ActorTaskQueueNode expectedNode, final ActorTaskQueueNode updateNode) {
    return TAIL_VAR.compareAndSet(this, expectedNode, updateNode);
  }
}

@SuppressWarnings("restriction")
class ActorTaskQueueNode {
  protected static final VarHandle PREV_VAR;
  protected static final VarHandle NEXT_VAR;

  static {
    try {
      PREV_VAR =
          MethodHandles.lookup()
              .findVarHandle(ActorTaskQueueNode.class, "prev", ActorTaskQueueNode.class);
      NEXT_VAR =
          MethodHandles.lookup()
              .findVarHandle(ActorTaskQueueNode.class, "next", ActorTaskQueueNode.class);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @SuppressFBWarnings(
      value = "UWF_UNWRITTEN_FIELD",
      justification = "Written using VarHandle NEXT_VAR")
  volatile ActorTaskQueueNode next;

  @SuppressFBWarnings(
      value = "UWF_UNWRITTEN_FIELD",
      justification = "Written using VarHandle PREV_VAR")
  volatile ActorTaskQueueNode prev;

  long stateCount;
  ActorTask task;

  void nextOrdered(final ActorTaskQueueNode t) {
    assert t != this;
    NEXT_VAR.setRelease(this, t);
  }

  void prevOrdered(final ActorTaskQueueNode t) {
    assert t != this;
    PREV_VAR.setRelease(this, t);
  }

  public void setTask(final ActorTask task) {
    this.task = task;
  }
}

@SuppressWarnings("restriction")
class ActorTaskQueuePadding1 {
  protected static final VarHandle HEAD_VAR;
  protected static final VarHandle TAIL_VAR;

  static {
    try {
      HEAD_VAR =
          MethodHandles.lookup()
              .findVarHandle(ActorTaskQueueHead.class, "head", ActorTaskQueueNode.class);
      TAIL_VAR =
          MethodHandles.lookup()
              .findVarHandle(ActorTaskQueueTail.class, "tail", ActorTaskQueueNode.class);
    } catch (final Exception ex) {
      throw new UnsupportedOperationException(ex);
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
