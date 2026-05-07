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
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
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
 * <p>Phase 1 scope: builder + lambda capture. The runner pipeline ({@code run()}, cluster
 * provisioning, deployment, worker registration) lands in Phase 2.
 *
 * <p>Lambda placeholder convention: the {@link #serviceTask(String, Function)} / {@link
 * #serviceTask(String, Consumer)} overloads (and the {@code userTask} equivalents) set {@code
 * zeebeJobType} on the BPMN element to <em>the elementId itself</em>. Phase 2 rewrites this to the
 * prefixed form ({@code <runId>-<elementId>}) before deployment.
 */
public final class LiveBpmn {

  private final BpmnModelInstance adoptedModel;
  private final Map<String, BoundHandler> bindings = new LinkedHashMap<>();
  private Object currentBuilder;

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
    return this;
  }

  public LiveBpmn startEvent(final String id) {
    currentBuilder = asProcessBuilder().startEvent(id);
    return this;
  }

  public LiveBpmn endEvent() {
    currentBuilder = asFlowNodeBuilder().endEvent();
    return this;
  }

  public LiveBpmn endEvent(final String id) {
    currentBuilder = asFlowNodeBuilder().endEvent(id);
    return this;
  }

  public LiveBpmn serviceTask(final String id, final Consumer<ServiceTaskBuilder> configure) {
    currentBuilder = asFlowNodeBuilder().serviceTask(id, configure);
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
    bindings.put(id, new BoundHandler.OfFunction(handler));
    return this;
  }

  /** Consumer-form service task; same placeholder rules as the function form. */
  public LiveBpmn serviceTask(final String id, final JobConsumer handler) {
    final ServiceTaskBuilder builder = asFlowNodeBuilder().serviceTask(id);
    builder.zeebeJobType(id);
    currentBuilder = builder;
    bindings.put(id, new BoundHandler.OfConsumer(handler));
    return this;
  }

  public LiveBpmn userTask(final String id, final Consumer<UserTaskBuilder> configure) {
    currentBuilder = asFlowNodeBuilder().userTask(id, configure);
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
    currentBuilder = builder;
    bindings.put(id, new BoundHandler.OfFunction(handler));
    return this;
  }

  /** See note on {@link #userTask(String, Function)}. */
  public LiveBpmn userTask(final String id, final JobConsumer handler) {
    final UserTaskBuilder builder = asFlowNodeBuilder().userTask(id);
    currentBuilder = builder;
    bindings.put(id, new BoundHandler.OfConsumer(handler));
    return this;
  }

  public LiveBpmn exclusiveGateway() {
    currentBuilder = asFlowNodeBuilder().exclusiveGateway();
    return this;
  }

  public LiveBpmn exclusiveGateway(final String id) {
    currentBuilder = asFlowNodeBuilder().exclusiveGateway(id);
    return this;
  }

  public LiveBpmn parallelGateway() {
    currentBuilder = asFlowNodeBuilder().parallelGateway();
    return this;
  }

  public LiveBpmn parallelGateway(final String id) {
    currentBuilder = asFlowNodeBuilder().parallelGateway(id);
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
  // Bindings for adopted models
  // ---------------------------------------------------------------------------

  public LiveBpmn bind(final String elementId, final Function<Job, Map<String, Object>> handler) {
    requireServiceTask(elementId);
    bindings.put(elementId, new BoundHandler.OfFunction(handler));
    return this;
  }

  public LiveBpmn bind(final String elementId, final JobConsumer handler) {
    requireServiceTask(elementId);
    bindings.put(elementId, new BoundHandler.OfConsumer(handler));
    return this;
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

  /** Package-private: lambda bindings keyed by element id, for the runner pipeline. */
  Map<String, BoundHandler> bindings() {
    return Collections.unmodifiableMap(bindings);
  }

  // ---------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------

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
