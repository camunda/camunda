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
package io.camunda.process.test.impl.spec.dsl.actions;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.impl.spec.SpecTestContext;
import io.camunda.process.test.impl.spec.dsl.AbstractSpecInstruction;
import io.camunda.process.test.impl.spec.dsl.SpecAction;
import java.util.List;
import org.awaitility.Awaitility;

public class CompleteJobAction extends AbstractSpecInstruction implements SpecAction {

  private String jobType;
  private String variables = "{}";

  public String getVariables() {
    return variables;
  }

  public void setVariables(final String variables) {
    this.variables = variables;
  }

  public String getJobType() {
    return jobType;
  }

  public void setJobType(final String jobType) {
    this.jobType = jobType;
  }

  @Override
  public void execute(
      final SpecTestContext testContext, final CamundaProcessTestContext processTestContext) {

    Awaitility.await()
        .untilAsserted(
            () -> {
              final List<ActivatedJob> activatedJobs = activateJobs(testContext);

              assertThat(activatedJobs)
                  .describedAs("No jobs found of type '%s'", jobType)
                  .hasSize(1);

              activatedJobs.forEach(job -> completeJob(testContext, job));
            });
  }

  private List<ActivatedJob> activateJobs(final SpecTestContext testContext) {
    return testContext
        .getCamundaClient()
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(1)
        .send()
        .join()
        .getJobs();
  }

  private void completeJob(final SpecTestContext testContext, final ActivatedJob activatedJob) {
    testContext
        .getCamundaClient()
        .newCompleteCommand(activatedJob.getKey())
        .variables(variables)
        .send()
        .join();
  }
}
