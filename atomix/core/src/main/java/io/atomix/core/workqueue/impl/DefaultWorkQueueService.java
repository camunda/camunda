/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.core.workqueue.impl;

import static com.google.common.base.Throwables.throwIfUnchecked;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import io.atomix.core.workqueue.Task;
import io.atomix.core.workqueue.WorkQueueStats;
import io.atomix.core.workqueue.WorkQueueType;
import io.atomix.primitive.service.AbstractPrimitiveService;
import io.atomix.primitive.service.BackupInput;
import io.atomix.primitive.service.BackupOutput;
import io.atomix.primitive.session.Session;
import io.atomix.primitive.session.SessionId;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Serializer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** State machine for {@link WorkQueueProxy} resource. */
public class DefaultWorkQueueService extends AbstractPrimitiveService<WorkQueueClient>
    implements WorkQueueService {

  private static final Serializer SERIALIZER =
      Serializer.using(
          Namespace.builder()
              .register(WorkQueueType.instance().namespace())
              .register(TaskAssignment.class)
              .register(new HashMap().keySet().getClass())
              .register(ArrayDeque.class)
              .register(SessionId.class)
              .build());

  private final AtomicLong totalCompleted = new AtomicLong(0);

  private Queue<Task<byte[]>> unassignedTasks = Queues.newArrayDeque();
  private Map<String, TaskAssignment> assignments = Maps.newHashMap();
  private Set<SessionId> registeredWorkers = Sets.newLinkedHashSet();

  public DefaultWorkQueueService() {
    super(WorkQueueType.instance(), WorkQueueClient.class);
  }

  @Override
  public Serializer serializer() {
    return SERIALIZER;
  }

  @Override
  public void onExpire(final Session session) {
    evictWorker(session.sessionId());
  }

  @Override
  public void onClose(final Session session) {
    evictWorker(session.sessionId());
  }

  @Override
  public void backup(final BackupOutput writer) {
    writer.writeObject(registeredWorkers);
    writer.writeObject(assignments);
    writer.writeObject(unassignedTasks);
    writer.writeLong(totalCompleted.get());
  }

  @Override
  public void restore(final BackupInput reader) {
    registeredWorkers = reader.readObject();
    assignments = reader.readObject();
    unassignedTasks = reader.readObject();
    totalCompleted.set(reader.readLong());
  }

  @Override
  public void add(final Collection<byte[]> items) {
    final AtomicInteger itemIndex = new AtomicInteger(0);
    items.forEach(
        item -> {
          final String taskId =
              String.format(
                  "%d:%d:%d",
                  getCurrentSession().sessionId().id(),
                  getCurrentIndex(),
                  itemIndex.getAndIncrement());
          unassignedTasks.add(new Task<>(taskId, item));
        });

    // Send an event to all sessions that have expressed interest in task processing
    // and are not actively processing a task.
    registeredWorkers.forEach(
        sessionId -> getSession(sessionId).accept(client -> client.taskAvailable()));
    // FIXME: This generates a lot of event traffic.
  }

  @Override
  public Collection<Task<byte[]>> take(final int maxTasks) {
    try {
      if (unassignedTasks.isEmpty()) {
        return ImmutableList.of();
      }
      final long sessionId = getCurrentSession().sessionId().id();
      return IntStream.range(0, Math.min(maxTasks, unassignedTasks.size()))
          .mapToObj(
              i -> {
                final Task<byte[]> task = unassignedTasks.poll();
                final String taskId = task.taskId();
                final TaskAssignment assignment = new TaskAssignment(sessionId, task);

                // bookkeeping
                assignments.put(taskId, assignment);

                return task;
              })
          .collect(Collectors.toCollection(ArrayList::new));
    } catch (final Exception e) {
      getLogger().warn("State machine update failed", e);
      throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void complete(final Collection<String> taskIds) {
    try {
      taskIds.forEach(
          taskId -> {
            final TaskAssignment assignment = assignments.get(taskId);
            if (assignment != null
                && assignment.sessionId() == getCurrentSession().sessionId().id()) {
              assignments.remove(taskId);
              // bookkeeping
              totalCompleted.incrementAndGet();
            }
          });
    } catch (final Exception e) {
      getLogger().warn("State machine update failed", e);
      throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public WorkQueueStats stats() {
    return WorkQueueStats.builder()
        .withTotalCompleted(totalCompleted.get())
        .withTotalPending(unassignedTasks.size())
        .withTotalInProgress(assignments.size())
        .build();
  }

  @Override
  public void register() {
    registeredWorkers.add(getCurrentSession().sessionId());
  }

  @Override
  public void unregister() {
    registeredWorkers.remove(getCurrentSession().sessionId());
  }

  @Override
  public void clear() {
    unassignedTasks.clear();
    assignments.clear();
    registeredWorkers.clear();
    totalCompleted.set(0);
  }

  private void evictWorker(final SessionId sessionId) {
    registeredWorkers.remove(sessionId);

    // TODO: Maintain an index of tasks by session for efficient access.
    final Iterator<Map.Entry<String, TaskAssignment>> iter = assignments.entrySet().iterator();
    while (iter.hasNext()) {
      final Map.Entry<String, TaskAssignment> entry = iter.next();
      final TaskAssignment assignment = entry.getValue();
      if (assignment.sessionId() == sessionId.id()) {
        unassignedTasks.add(assignment.task());
        iter.remove();
      }
    }
  }

  private static class TaskAssignment {
    private final long sessionId;
    private final Task<byte[]> task;

    TaskAssignment(final long sessionId, final Task<byte[]> task) {
      this.sessionId = sessionId;
      this.task = task;
    }

    public long sessionId() {
      return sessionId;
    }

    public Task<byte[]> task() {
      return task;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(getClass())
          .add("sessionId", sessionId)
          .add("task", task)
          .toString();
    }
  }
}
