/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.optimize.qa.performance.steps.decorator;

import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStep;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeMeasurementTestStepDecorator extends PerfTestStep {

  private Logger logger;

  private PerfTestStep testStep;

  public TimeMeasurementTestStepDecorator(PerfTestStep testStep) {
    this.testStep = testStep;
    logger = LoggerFactory.getLogger(testStep.getTestStepClass());
  }

  @Override
  public Class getTestStepClass() {
    return testStep.getTestStepClass();
  }

  @Override
  public PerfTestStepResult execute(PerfTestContext context) {

    long stepStartTime, stepEndTime;
    logger.info("Starting  " + testStep.getClass().getSimpleName());
    stepStartTime = System.currentTimeMillis();

    PerfTestStepResult result = testStep.execute(context);

    stepEndTime = System.currentTimeMillis();
    long totalTime = stepEndTime - stepStartTime;
    result.setDurationInMs(totalTime);
    logger.info("Finished " + testStep.getClass().getSimpleName());
    logger.info("Step took " + totalTime + " ms to finish!\n");

    return result;
  }
}
