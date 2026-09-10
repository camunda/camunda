/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.camunda.runner;

import io.camunda.runner.internal.BindingKey;
import io.camunda.runner.internal.BoundHandler;
import io.camunda.runner.internal.LocalContainerCluster;
import io.camunda.runner.internal.RunnerPipeline;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowElementBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.ZeebeExecutionListenersBuilder;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListeners;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListeners;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/**
 * Single class that is both the LiveBpmn facade and the fluent builder type.
 *
 * <p>Static factories construct an instance ({@link #createExecutableProcess(String)}, {@link
 * #of(BpmnModelInstance)}); instance methods continue the chain and mirror the bpmn-model builder
 * surface for the subset that LiveBpmn hand-wraps. Anything not mirrored is reachable via {@link
 * #raw()}.
 *
 * <p>Lambda placeholder convention: the {@link #serviceTask(String, Function)} / {@link
 * #serviceTask(String, Consumer)} overloads (and the {@code userTask} equivalents) set {@code
 * zeebeJobType} on the BPMN element to <em>the elementId itself</em>. The {@code .on(eventType,
 * lambda)} overloads (execution + task listeners) set the listener {@code type} attribute to a
 * structured placeholder ({@code <elementId>:el:start}, {@code <elementId>:tl:assigning}, etc.).
 * The runner rewrites all placeholders to the prefixed form ({@code <runId>-<placeholder>}) before
 * deployment.
 */
public final class LiveBpmn {

  private final BpmnModelInstance adoptedModel;
  private final Map<BindingKey, BoundHandler> bindings = new LinkedHashMap<>();
  private Object currentBuilder;

  /**
   * The id of the most recent flow node that listeners can attach to. Updated by {@link
   * #serviceTask}, {@link #userTask}, {@link #startEvent}, {@link #endEvent}, {@link
   * #exclusiveGateway}, {@link #parallelGateway}. Listener attachment methods read this; they do
   * not mutate it.
   */
  private String lastAttachableId;

  /** Tracks whether the last attachable element is a UserTask (for task-listener guards). */
  private boolean lastAttachableIsUserTask;

  private LiveBpmn(final ProcessBuilder processBuilder) {
    this.currentBuilder = processBuilder;
    this.adoptedModel = null;
  }

  private LiveBpmn(final BpmnModelInstance model) {
    this.currentBuilder = null;
    this.adoptedModel = model;
  }

  // ---------------------------------------------------------------------------
  // Static factories
  // ---------------------------------------------------------------------------

  public static LiveBpmn createExecutableProcess(final String id) {
    return new LiveBpmn(Bpmn.createExecutableProcess(id));
  }

  public static LiveBpmn of(final BpmnModelInstance existingModel) {
    return new LiveBpmn(existingModel);
  }

  /**
   * Loads a {@code .bpmn} file from disk (e.g. one exported from Camunda Modeler) and adopts it as
   * the model. Bindings can then be attached via {@link #bind} by element id.
   *
   * @throws IllegalArgumentException if the file does not exist
   * @throws UncheckedIOException if reading the file fails
   */
  public static LiveBpmn fromFile(final Path bpmnFile) {
    Objects.requireNonNull(bpmnFile, "bpmnFile");
    if (!Files.exists(bpmnFile)) {
      throw new IllegalArgumentException("BPMN file does not exist: " + bpmnFile);
    }
    return of(Bpmn.readModelFromFile(bpmnFile.toFile()));
  }

  /**
   * Loads a {@code .bpmn} resource from the classpath (e.g. {@code processes/order.bpmn} packaged
   * under {@code src/main/resources}) and adopts it as the model.
   *
   * @throws IllegalArgumentException if the resource cannot be found
   */
  public static LiveBpmn fromClasspath(final String resource) {
    Objects.requireNonNull(resource, "resource");
    final ClassLoader cl =
        Thread.currentThread().getContextClassLoader() != null
            ? Thread.currentThread().getContextClassLoader()
            : LiveBpmn.class.getClassLoader();
    try (InputStream in = cl.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalArgumentException("classpath resource not found: " + resource);
      }
      return of(Bpmn.readModelFromStream(in));
    } catch (final IOException e) {
      throw new UncheckedIOException("failed to read classpath resource: " + resource, e);
    }
  }

  /** Returns a fresh {@link ClusterFactory} for building {@link Cluster} specs. */
  public static ClusterFactory cluster() {
    return new ClusterFactory();
  }

  // ---------------------------------------------------------------------------
  // Run triggers
  // ---------------------------------------------------------------------------

  /**
   * Runs {@code n} instances against a freshly-booted Testcontainer (smart default for Phase 2).
   */
  public Run run(final int n) {
    return run(n, new LocalContainerCluster());
  }

  /** Runs {@code n} instances against the supplied {@link Cluster}. */
  public Run run(final int n, final Cluster cluster) {
    return run(RunOptions.of(n), cluster);
  }

  /** Power form: runs the configured number of instances against a smart-default cluster. */
  public Run run(final RunOptions opts) {
    return run(opts, new LocalContainerCluster());
  }

  /** Power form: runs the configured options against the supplied {@link Cluster}. */
  public Run run(final RunOptions opts, final Cluster cluster) {
    return RunnerPipeline.execute(done(), bindings, opts, cluster);
  }

  // ---------------------------------------------------------------------------
  // Flow construction (mirrors AbstractFlowNodeBuilder / ProcessBuilder)
  // ---------------------------------------------------------------------------

  public LiveBpmn startEvent() {
    currentBuilder = asProcessBuilder().startEvent();
    setAttachable(null, false); // unnamed element — listeners won't have a stable target
    return this;
  }

  public LiveBpmn startEvent(final String id) {
    currentBuilder = asProcessBuilder().startEvent(id);
    setAttachable(id, false);
    return this;
  }

  public LiveBpmn endEvent() {
    currentBuilder = asFlowNodeBuilder().endEvent();
    setAttachable(null, false);
    return this;
  }

  public LiveBpmn endEvent(final String id) {
    currentBuilder = asFlowNodeBuilder().endEvent(id);
    setAttachable(id, false);
    return this;
  }

  public LiveBpmn serviceTask(final String id, final Consumer<ServiceTaskBuilder> configure) {
    currentBuilder = asFlowNodeBuilder().serviceTask(id, configure);
    setAttachable(id, false);
    return this;
  }

  /**
   * Function-form service task: the lambda is recorded in {@link #bindings()} and a placeholder
   * {@code zeebeJobType} is set to the elementId. Phase 2 prefixes the type before deploy.
   */
  public LiveBpmn serviceTask(final String id, final Function<Job, Map<String, Object>> handler) {
    final ServiceTaskBuilder builder = asFlowNodeBuilder().serviceTask(id);
    builder.zeebeJobType(id);
    currentBuilder = builder;
    bindings.put(BindingKey.serviceTask(id), new BoundHandler.OfFunction(handler));
    setAttachable(id, false);
    return this;
  }

  /** Consumer-form service task; same placeholder rules as the function form. */
  public LiveBpmn serviceTask(final String id, final JobConsumer handler) {
    final ServiceTaskBuilder builder = asFlowNodeBuilder().serviceTask(id);
    builder.zeebeJobType(id);
    currentBuilder = builder;
    bindings.put(BindingKey.serviceTask(id), new BoundHandler.OfConsumer(handler));
    setAttachable(id, false);
    return this;
  }

  /**
   * Mark every {@code userTask(...)} as a modern Camunda user task ({@code <zeebe:userTask/>}).
   * This is the only flavor that supports task listeners, and it's the recommended path on 8.6+.
   * Legacy job-based user tasks remain reachable via {@link #raw()} / a vanilla {@code
   * Bpmn.createExecutableProcess(...)} model adopted via {@link #of}.
   */
  public LiveBpmn userTask(final String id, final Consumer<UserTaskBuilder> configure) {
    final UserTaskBuilder b = asFlowNodeBuilder().userTask(id);
    b.zeebeUserTask();
    configure.accept(b);
    currentBuilder = b;
    setAttachable(id, true);
    return this;
  }

  public LiveBpmn userTask(final String id) {
    final UserTaskBuilder b = asFlowNodeBuilder().userTask(id);
    b.zeebeUserTask();
    currentBuilder = b;
    setAttachable(id, true);
    return this;
  }

  /**
   * User-task lambda overloads: in Camunda 8, modern user tasks are handled by Tasklist (not job
   * workers), and legacy "job-based" user tasks need an explicit BPMN extension that LiveBpmn does
   * not yet wire. The lambda is recorded for now; Phase 2 will decide the worker semantics.
   * Currently this captures the binding so the surface compiles, but the runner pipeline cannot
   * dispatch to it. Calling {@code .run()} on a model containing user-task lambdas will fail with a
   * clear message until that's resolved.
   */
  public LiveBpmn userTask(final String id, final Function<Job, Map<String, Object>> handler) {
    final UserTaskBuilder builder = asFlowNodeBuilder().userTask(id);
    builder.zeebeUserTask();
    currentBuilder = builder;
    bindings.put(BindingKey.serviceTask(id), new BoundHandler.OfFunction(handler));
    setAttachable(id, true);
    return this;
  }

  /** See note on {@link #userTask(String, Function)}. */
  public LiveBpmn userTask(final String id, final JobConsumer handler) {
    final UserTaskBuilder builder = asFlowNodeBuilder().userTask(id);
    builder.zeebeUserTask();
    currentBuilder = builder;
    bindings.put(BindingKey.serviceTask(id), new BoundHandler.OfConsumer(handler));
    setAttachable(id, true);
    return this;
  }

  public LiveBpmn exclusiveGateway() {
    currentBuilder = asFlowNodeBuilder().exclusiveGateway();
    setAttachable(null, false);
    return this;
  }

  public LiveBpmn exclusiveGateway(final String id) {
    currentBuilder = asFlowNodeBuilder().exclusiveGateway(id);
    setAttachable(id, false);
    return this;
  }

  public LiveBpmn parallelGateway() {
    currentBuilder = asFlowNodeBuilder().parallelGateway();
    setAttachable(null, false);
    return this;
  }

  public LiveBpmn parallelGateway(final String id) {
    currentBuilder = asFlowNodeBuilder().parallelGateway(id);
    setAttachable(id, false);
    return this;
  }

  /** Connects the current flow node to an already-defined element by id. */
  public LiveBpmn sequenceFlowTo(final String id) {
    currentBuilder = asFlowNodeBuilder().connectTo(id);
    return this;
  }

  /**
   * Repositions the builder cursor on a previously-defined node so a new branch can be added.
   * Mirror of {@link AbstractFlowNodeBuilder#moveToNode(String)}.
   */
  public LiveBpmn moveToNode(final String id) {
    currentBuilder = asFlowNodeBuilder().moveToNode(id);
    return this;
  }

  /**
   * Repositions the cursor onto the last-added gateway. Mirror of {@link
   * AbstractFlowNodeBuilder#moveToLastGateway()}.
   */
  public LiveBpmn moveToLastGateway() {
    currentBuilder = asFlowNodeBuilder().moveToLastGateway();
    return this;
  }

  /** Sets a sequence-flow condition expression on the most recent flow. */
  public LiveBpmn condition(final String expression) {
    currentBuilder = asFlowNodeBuilder().condition(expression);
    return this;
  }

  /** Sets the {@code name} attribute on the most-recently-added flow element. */
  public LiveBpmn name(final String elementName) {
    if (!(currentBuilder instanceof AbstractFlowElementBuilder<?, ?> b)) {
      throw new IllegalStateException(
          "name() requires a flow-element builder as current position; got "
              + (currentBuilder == null ? "null" : currentBuilder.getClass().getName()));
    }
    b.name(elementName);
    return this;
  }

  // ---------------------------------------------------------------------------
  // Listener attachment (inline form)
  // ---------------------------------------------------------------------------

  /**
   * Attaches an execution listener (start or end) to the most-recently-declared flow node. With a
   * static import of {@link ZeebeExecutionListenerEventType}, the call site reads {@code .on(start,
   * lambda)} or {@code .on(end, lambda)}.
   */
  public LiveBpmn on(
      final ZeebeExecutionListenerEventType eventType,
      final Function<Job, Map<String, Object>> handler) {
    return attachExecutionListener(eventType.name(), new BoundHandler.OfFunction(handler));
  }

  /** See {@link #on(ZeebeExecutionListenerEventType, Function)}. */
  public LiveBpmn on(final ZeebeExecutionListenerEventType eventType, final JobConsumer handler) {
    return attachExecutionListener(eventType.name(), new BoundHandler.OfConsumer(handler));
  }

  /** Attaches a task listener to the most-recently-declared user task. */
  public LiveBpmn on(
      final ZeebeTaskListenerEventType eventType,
      final Function<Job, Map<String, Object>> handler) {
    return attachTaskListener(eventType, new BoundHandler.OfFunction(handler));
  }

  /** See {@link #on(ZeebeTaskListenerEventType, Function)}. */
  public LiveBpmn on(final ZeebeTaskListenerEventType eventType, final JobConsumer handler) {
    return attachTaskListener(eventType, new BoundHandler.OfConsumer(handler));
  }

  /**
   * Brackets-grouped form of listener attachment. See {@link Listeners}. Equivalent to writing the
   * {@code .on(...)} / {@code .on(...)} calls flat — purely a readability sugar.
   */
  public LiveBpmn listeners(final Consumer<Listeners> configure) {
    configure.accept(new Listeners(this));
    return this;
  }

  // ---------------------------------------------------------------------------
  // Bindings for adopted models
  // ---------------------------------------------------------------------------

  public LiveBpmn bind(final String elementId, final Function<Job, Map<String, Object>> handler) {
    requireServiceTask(elementId);
    bindings.put(BindingKey.serviceTask(elementId), new BoundHandler.OfFunction(handler));
    return this;
  }

  public LiveBpmn bind(final String elementId, final JobConsumer handler) {
    requireServiceTask(elementId);
    bindings.put(BindingKey.serviceTask(elementId), new BoundHandler.OfConsumer(handler));
    return this;
  }

  /**
   * Binds an execution listener (start or end) to an existing element by id, for adopted models.
   * Mirrors the inline {@link #on(ZeebeExecutionListenerEventType, Function)} form.
   */
  public LiveBpmn on(
      final String elementId,
      final ZeebeExecutionListenerEventType eventType,
      final Function<Job, Map<String, Object>> handler) {
    return bindExecutionListener(elementId, eventType.name(), new BoundHandler.OfFunction(handler));
  }

  /** See {@link #on(String, ZeebeExecutionListenerEventType, Function)}. */
  public LiveBpmn on(
      final String elementId,
      final ZeebeExecutionListenerEventType eventType,
      final JobConsumer handler) {
    return bindExecutionListener(elementId, eventType.name(), new BoundHandler.OfConsumer(handler));
  }

  /**
   * Binds a task listener to an existing user task by id, for adopted models. Mirrors the inline
   * {@link #on(ZeebeTaskListenerEventType, Function)} form.
   */
  public LiveBpmn on(
      final String elementId,
      final ZeebeTaskListenerEventType eventType,
      final Function<Job, Map<String, Object>> handler) {
    return bindTaskListener0(elementId, eventType, new BoundHandler.OfFunction(handler));
  }

  /** See {@link #on(String, ZeebeTaskListenerEventType, Function)}. */
  public LiveBpmn on(
      final String elementId,
      final ZeebeTaskListenerEventType eventType,
      final JobConsumer handler) {
    return bindTaskListener0(elementId, eventType, new BoundHandler.OfConsumer(handler));
  }

  // ---------------------------------------------------------------------------
  // Escape hatches / terminal
  // ---------------------------------------------------------------------------

  /** Returns the underlying bpmn-model builder for un-mirrored methods. */
  public AbstractFlowNodeBuilder<?, ?> raw() {
    if (!(currentBuilder instanceof AbstractFlowNodeBuilder<?, ?> b)) {
      throw new IllegalStateException(
          "raw() requires a flow-node builder as current position; got "
              + (currentBuilder == null ? "null" : currentBuilder.getClass().getName()));
    }
    return b;
  }

  /** Returns the underlying {@link BpmnModelInstance}. Drop-in for {@code Bpmn...done()}. */
  public BpmnModelInstance done() {
    return adoptedModel != null ? adoptedModel : currentModel();
  }

  /** Package-private: lambda bindings keyed by binding key, for the runner pipeline. */
  Map<BindingKey, BoundHandler> bindings() {
    return Collections.unmodifiableMap(bindings);
  }

  // ---------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------

  private void setAttachable(final String id, final boolean isUserTask) {
    this.lastAttachableId = id;
    this.lastAttachableIsUserTask = isUserTask;
  }

  private LiveBpmn attachExecutionListener(final String startOrEnd, final BoundHandler handler) {
    if (lastAttachableId == null) {
      throw new IllegalStateException(
          "on"
              + capitalize(startOrEnd)
              + "() requires a preceding attachable element"
              + " (serviceTask/userTask/startEvent/endEvent/gateway with an explicit id)");
    }
    if (!(currentBuilder instanceof ZeebeExecutionListenersBuilder<?> listenersBuilder)) {
      throw new IllegalStateException(
          "on"
              + capitalize(startOrEnd)
              + "() requires a builder supporting execution listeners,"
              + " but got "
              + currentBuilder.getClass().getName());
    }
    final BindingKey key = BindingKey.executionListener(lastAttachableId, startOrEnd);
    final String placeholder = key.placeholderType();
    if ("start".equals(startOrEnd)) {
      listenersBuilder.zeebeStartExecutionListener(placeholder);
    } else {
      listenersBuilder.zeebeEndExecutionListener(placeholder);
    }
    bindings.put(key, handler);
    return this;
  }

  private LiveBpmn attachTaskListener(
      final ZeebeTaskListenerEventType eventType, final BoundHandler handler) {
    Objects.requireNonNull(eventType, "eventType");
    if (lastAttachableId == null) {
      throw new IllegalStateException("on(...) requires a preceding userTask(...) call");
    }
    if (!lastAttachableIsUserTask) {
      throw new IllegalStateException(
          "on(...) can only attach to a userTask; last attachable element '"
              + lastAttachableId
              + "' is not a UserTask");
    }
    if (!(currentBuilder instanceof UserTaskBuilder userTaskBuilder)) {
      throw new IllegalStateException(
          "on(...) requires the current builder to be a UserTaskBuilder; got "
              + currentBuilder.getClass().getName());
    }
    final BindingKey key = BindingKey.taskListener(lastAttachableId, eventType);
    final String placeholder = key.placeholderType();
    final ZeebeTaskListenerEventType resolved = eventType.resolve();
    userTaskBuilder.zeebeTaskListener(b -> b.eventType(resolved).type(placeholder));
    bindings.put(key, handler);
    return this;
  }

  private LiveBpmn bindExecutionListener(
      final String elementId, final String startOrEnd, final BoundHandler handler) {
    final BpmnModelInstance model = adoptedModel != null ? adoptedModel : currentModel();
    final ModelElementInstance element = model.getModelElementById(elementId);
    if (element == null) {
      throw new IllegalArgumentException(
          "no element with id '" + elementId + "' exists in the model");
    }
    if (!(element instanceof io.camunda.zeebe.model.bpmn.instance.BaseElement base)) {
      throw new IllegalArgumentException(
          "element '" + elementId + "' does not support execution listeners");
    }
    final ZeebeExecutionListeners listeners =
        base.getSingleExtensionElement(ZeebeExecutionListeners.class);
    final ZeebeExecutionListenerEventType wantEvent =
        "start".equals(startOrEnd)
            ? ZeebeExecutionListenerEventType.start
            : ZeebeExecutionListenerEventType.end;
    boolean found = false;
    if (listeners != null) {
      for (final ZeebeExecutionListener l : listeners.getExecutionListeners()) {
        if (l.getEventType() == wantEvent) {
          found = true;
          break;
        }
      }
    }
    if (!found) {
      throw new IllegalArgumentException(
          "element '"
              + elementId
              + "' has no <zeebe:executionListener eventType=\""
              + startOrEnd
              + "\"/> in the adopted model; bind"
              + capitalize(startOrEnd)
              + " requires the listener to already exist");
    }
    bindings.put(BindingKey.executionListener(elementId, startOrEnd), handler);
    return this;
  }

  private LiveBpmn bindTaskListener0(
      final String elementId,
      final ZeebeTaskListenerEventType eventType,
      final BoundHandler handler) {
    Objects.requireNonNull(eventType, "eventType");
    final BpmnModelInstance model = adoptedModel != null ? adoptedModel : currentModel();
    final ModelElementInstance element = model.getModelElementById(elementId);
    if (element == null) {
      throw new IllegalArgumentException(
          "no element with id '" + elementId + "' exists in the model");
    }
    if (!(element instanceof UserTask userTask)) {
      throw new IllegalArgumentException(
          "element '" + elementId + "' is not a UserTask; task listeners require a UserTask");
    }
    final ZeebeTaskListeners listeners =
        userTask.getSingleExtensionElement(ZeebeTaskListeners.class);
    final ZeebeTaskListenerEventType resolved = eventType.resolve();
    boolean found = false;
    if (listeners != null) {
      for (final ZeebeTaskListener l : listeners.getTaskListeners()) {
        final ZeebeTaskListenerEventType lEvent = l.getEventType();
        if (lEvent != null && lEvent.resolve() == resolved) {
          found = true;
          break;
        }
      }
    }
    if (!found) {
      throw new IllegalArgumentException(
          "user task '"
              + elementId
              + "' has no <zeebe:taskListener eventType=\""
              + resolved.name()
              + "\"/> in the adopted model; bindTaskListener requires the listener to already"
              + " exist");
    }
    bindings.put(BindingKey.taskListener(elementId, eventType), handler);
    return this;
  }

  private static String capitalize(final String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private ProcessBuilder asProcessBuilder() {
    if (currentBuilder instanceof ProcessBuilder pb) {
      return pb;
    }
    throw new IllegalStateException(
        "current builder is not a ProcessBuilder: "
            + (currentBuilder == null ? "null" : currentBuilder.getClass().getName()));
  }

  private AbstractFlowNodeBuilder<?, ?> asFlowNodeBuilder() {
    if (currentBuilder instanceof AbstractFlowNodeBuilder<?, ?> b) {
      return b;
    }
    throw new IllegalStateException(
        "current builder is not an AbstractFlowNodeBuilder: "
            + (currentBuilder == null ? "null" : currentBuilder.getClass().getName()));
  }

  /** Resolves the in-progress {@link BpmnModelInstance} from whatever builder is current. */
  private BpmnModelInstance currentModel() {
    if (currentBuilder instanceof AbstractFlowNodeBuilder<?, ?> b) {
      return b.done();
    }
    if (currentBuilder instanceof ProcessBuilder pb) {
      return pb.done();
    }
    if (currentBuilder instanceof AbstractFlowElementBuilder<?, ?> fe) {
      return fe.done();
    }
    throw new IllegalStateException("cannot resolve underlying model from current builder");
  }

  private void requireServiceTask(final String elementId) {
    final BpmnModelInstance model = adoptedModel != null ? adoptedModel : currentModel();
    final ModelElementInstance element = model.getModelElementById(elementId);
    if (element == null) {
      throw new IllegalArgumentException(
          "no element with id '" + elementId + "' exists in the model");
    }
    if (!(element instanceof ServiceTask)) {
      throw new IllegalArgumentException(
          "element '"
              + elementId
              + "' is not a service task (got "
              + element.getClass().getSimpleName()
              + "); only service tasks support lambda bindings");
    }
  }
}
