package io.camunda.zeebe.util.startup.actor;

import io.camunda.zeebe.util.sched.future.ActorFuture;

/**
 * Base interface for bootstrap steps. Each step implements the startup logic in {@code
 * startup(...)}, as well as, the corresponding shutdown logic in {@code shutdown(...)}. Both
 * methods take a context object as argument and return a future of the same context object.<br>
 * Typically, a startup step will create resources and call setters on the context object, whereas a
 * shutdown step will shutdown resources and remove them from the context by setting them to {@code
 * null}. <br>
 * Contract:
 *
 * <ul>
 *   <li>{@link #shutdown(Object)} will never be called before {@link #startup(Object)}
 *   <li>{@link #startup(Object)} will be called at most once
 *   <li>@link #shutdown(Object)} may be called more than once with the expectation that the first
 *       call will trigger the shutdown and any subsequent calls shall do nothing
 *   <li>@link #shutdown(Object)} may be called before the future of startup has completed with the
 *       expectation that it will either cancel the startup process, or it will wait until it
 *       terminates and then immediately begin the shutdown
 * </ul>
 *
 * @param <CONTEXT> context object for the startup and shutdown steps. During startup this context
 *     is used to collect the resources created during startup; during shutdown it is used to set
 *     resources that have been shut down to {@code null}
 */
public interface BootstrapStep<CONTEXT> {
  /**
   * Returns name for observability
   *
   * @return name for observability
   */
  String getName();

  /**
   * Executes the startup logic
   *
   * @param context the startup context at the start of this step
   */
  ActorFuture<CONTEXT> startup(final CONTEXT context);

  /**
   * Executes the shutdown logic
   *
   * @param context the shutdown context at the start of this step
   */
  ActorFuture<CONTEXT> shutdown(final CONTEXT context);
}
