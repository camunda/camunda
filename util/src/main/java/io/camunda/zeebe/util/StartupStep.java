package io.camunda.zeebe.util;

import java.util.function.Consumer;

/**
 * Base interface for startup (and shutdown) steps. Each step implements the startup logic in {@code
 * startup(...)}, as well as, the corresponding shutdown logic in {@code shutdown(...)}. Both
 * methods take a context object as argument and return a future of the same context object.<br>
 * Typically, a startup step will create resources and call setters on the context object, whereas a
 * shutdown step will shutdown resources and remove them from the context by setting them to {@code
 * null}. <br>
 * Contract:
 *
 * <ul>
 *   <li>Shutdown will never be called before startup
 *   <li>Startup will be called at most once
 *   <li>Shutdown may be called more than once with the expectation that the first call will trigger
 *       the shutdown and any subsequent calls shall do nothing
 *   <li>Shutdown may be called before the future of startup has completed with the expectation that
 *       it will either cancel the startup process, or it will wait until it terminates and then
 *       immediately begin the shutdown
 * </ul>
 *
 * @param <CONTEXT> context object for the startup and shutdown steps. During startup this context
 *     is used to collect the resources created during startup; during shutdown it is used to set
 *     resources that have been shut down to {@code null}
 */
public interface StartupStep<CONTEXT> {

  /**
   * Returns name for logging purposes
   *
   * @return name for logging purposes
   */
  String getName();

  /**
   * Executes the startup logic
   *
   * @param context the startup context at the start of this step
   * @param callback callback that receives the modified startup context at the end of this step
   */
  void startup(final CONTEXT context, Consumer<Either<Throwable, CONTEXT>> callback);

  /**
   * Executes the shutdown logic
   *
   * @param context the shutdown context at the start of this step
   * @param callback callback that receives the modified shutdown context at the end of this step
   */
  void shutdown(final CONTEXT context, Consumer<Either<Throwable, CONTEXT>> callback);
}
