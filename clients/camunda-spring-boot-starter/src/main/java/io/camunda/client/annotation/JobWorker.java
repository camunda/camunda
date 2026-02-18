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
package io.camunda.client.annotation;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.exception.BpmnError;
import io.camunda.client.exception.JobError;
import io.camunda.client.spring.annotation.processor.JobWorkerAnnotationProcessor;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JobWorker {

  /**
   * Set to empty string which leads to the method name being used (if not
   * ${camunda.client.worker.defaults.type} is configured) Implemented in {@link
   * JobWorkerAnnotationProcessor}
   */
  String type() default "";

  /**
   * set to empty string which leads to default from CamundaClientBuilderImpl being used in {@link
   * JobWorkerAnnotationProcessor}
   */
  String name() default "";

  /**
   * Set the time (in milliseconds) for how long a job is exclusively assigned for this worker.
   * During this time, the job cannot be assigned by other workers to ensure that only one worker
   * works on the job. When the time is over, then the job can be assigned again by this or other
   * worker if it's not completed yet. If no timeout is set, then the default is used from the
   * {@link CamundaClientConfiguration}
   */
  long timeout() default -1L;

  /**
   * Set the maximum number of jobs which will be exclusively activated for this worker at the same
   * time. This is used to control the backpressure of the worker. When the maximum is reached, then
   * the worker will stop activating new jobs to not overwhelm the client and give other workers the
   * chance to work on the jobs. The worker will try to activate new jobs again when jobs are
   * completed (or marked as failed). If no maximum is set, then the default from the {@link
   * CamundaClientConfiguration}, is used. <br>
   * <br>
   * Considerations: A greater value can avoid situations in which the client waits idle for the
   * broker to provide more jobs. This can improve the worker's throughput. The memory used by the
   * worker is linear with respect to this value. The job's timeout starts to run down as soon as
   * the broker pushes the job. Keep in mind that the following must hold to ensure fluent job
   * handling:
   *
   * <pre>time spent in queue + time job handler needs until job completion < job timeout</pre>
   */
  int maxJobsActive() default -1;

  /**
   * Set the request timeout (in seconds) for activate job request used to poll for new jobs. If no
   * request timeout is set then the default is used from the {@link CamundaClientConfiguration}
   */
  long requestTimeout() default -1L;

  /**
   * Set the maximal interval (in milliseconds) between polling for new jobs. A job worker will
   * automatically try to always activate new jobs after completing jobs. If no jobs can be
   * activated after completing, the worker will periodically poll for new jobs. If no poll interval
   * is set then the default is used from the {@link CamundaClientConfiguration}
   */
  long pollInterval() default -1L;

  /**
   * Set a list of variable names which should be fetched on job activation. The jobs which are
   * activated by this worker will only contain variables from this list. This can be used to limit
   * the number of variables of the activated jobs.
   */
  String[] fetchVariables() default {};

  /** If set to true, all variables are fetched. Can only be used as a singleton. */
  boolean[] fetchAllVariables() default {};

  /**
   * If set to true, the job is automatically completed after the worker code has finished. In this
   * case, your worker code is not allowed to complete the job itself.
   *
   * <p>You can still throw exceptions if you want to raise a problem instead of job completion. To
   * control the retry behavior or submit variables, you can use the {@link JobError}. You could
   * also raise a BPMN error throwing a {@link BpmnError}. Can only be used as a singleton.
   */
  boolean[] autoComplete() default {};

  /** If set to true, the worker will actually be subscribing. Can only be used as a singleton. */
  boolean[] enabled() default {};

  /** A list of tenants for this job will be worked on. */
  String[] tenantIds() default {};

  /**
   * Whether job streaming should be enabled for this job type. Useful in high-performance setups
   * but can only be used with a gRPC connection. Can only be used as a singleton.
   */
  boolean[] streamEnabled() default {};

  /** Stream timeout in ms */
  long streamTimeout() default -1L;

  /** Set the max number of retries for a job */
  int maxRetries() default -1;

  /** Set the retry backoff for a job (in milliseconds) */
  long retryBackoff() default -1L;

  /** Set the tenant filter mode for job activation */
  TenantFilter[] TenantFilter() default {};
}
