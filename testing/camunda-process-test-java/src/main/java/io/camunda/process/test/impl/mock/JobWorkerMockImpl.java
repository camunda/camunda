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
package io.camunda.process.test.impl.mock;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep2;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobWorkerMockImpl implements JobWorkerMock {

  /**
   * Prefix of the engine-internal job type a stubbed call activity's job carries; the called
   * process id is appended. Kept in sync with {@code io.camunda.zeebe.protocol.Protocol}.
   */
  static final String CALL_ACTIVITY_STUB_JOB_TYPE_PREFIX = "io.camunda.zeebe:callActivityStub";

  private static final Logger LOGGER = LoggerFactory.getLogger(JobWorkerMockImpl.class);

  private final List<ActivatedJob> activatedJobs = new ArrayList<>();

  public JobWorkerMockImpl(
      final String jobType,
      final CamundaClient camundaClient,
      final JobHandler jobHandler,
      final String reservationToken) {
    this(jobType, null, camundaClient, jobHandler, reservationToken);
  }

  public JobWorkerMockImpl(
      final String jobType,
      final String calledProcessId,
      final CamundaClient camundaClient,
      final JobHandler jobHandler,
      final String reservationToken) {

    final JobHandler loggingJobHandler =
        (jobClient, job) -> {
          LOGGER.debug(
              "Mock: Pass job to custom handler [jobType: '{}', jobKey: '{}']",
              jobType,
              job.getKey());

          activatedJobs.add(job);
          jobHandler.handle(jobClient, job);
        };

    final JobHandler safeLoggingJobHandler =
        (client, job) -> {
          try {
            loggingJobHandler.handle(client, job);
          } catch (final AssertionError e) {
            final String failureMessage =
                String.format(
                    "JobWorkerMock [jobType: %s, jobKey: %s] has failed assertions and will be terminated.",
                    jobType, job.getKey());
            System.err.println(failureMessage);
            e.printStackTrace();

            final FailJobCommandStep2 failCommand = client.newFailCommand(job.getKey()).retries(0);
            if (reservationToken != null) {
              failCommand.withReservationToken(reservationToken);
            }
            failCommand.send().join();
          }
        };

    final String subscriptionJobType =
        calledProcessId != null ? callActivityStubJobType(calledProcessId) : jobType;
    final JobWorkerBuilderStep2 subscription =
        camundaClient.newWorker().jobType(subscriptionJobType);
    final JobWorkerBuilderStep3 workerBuilder = subscription.handler(safeLoggingJobHandler);
    if (calledProcessId != null) {
      // a stub job is drained over the poll path, so disable streaming even when the client enables
      // it by default
      workerBuilder.streamEnabled(false);
    }
    if (reservationToken != null) {
      // a reserved job is never pushed to a stream, only served over the poll path, so disable
      // streaming even when the client enables it by default
      workerBuilder.streamEnabled(false);
      workerBuilder.reservationToken(reservationToken);
    }
    workerBuilder.open();
  }

  @Override
  public int getInvocations() {
    return activatedJobs.size();
  }

  @Override
  public List<ActivatedJob> getActivatedJobs() {
    return activatedJobs;
  }

  static String callActivityStubJobType(final String calledProcessId) {
    return CALL_ACTIVITY_STUB_JOB_TYPE_PREFIX + ":" + calledProcessId;
  }
}
