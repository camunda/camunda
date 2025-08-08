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
package io.camunda.spring.client.jobhandling;

import io.camunda.client.api.worker.JobHandler;
import java.util.List;

public interface JobHandlerFactory {
  Object invoke(final Object... args) throws Exception;

  JobHandler getJobHandler(JobHandlerFactoryContext context);

  String getGeneratedJobWorkerName();

  String getGeneratedJobWorkerType();

  boolean usesActivatedJob();

  List<String> getUsedVariableNames();
}
