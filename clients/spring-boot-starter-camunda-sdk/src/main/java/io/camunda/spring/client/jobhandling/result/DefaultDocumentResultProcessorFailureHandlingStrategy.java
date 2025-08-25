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
package io.camunda.spring.client.jobhandling.result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDocumentResultProcessorFailureHandlingStrategy
    implements DocumentResultProcessorFailureHandlingStrategy {
  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultDocumentResultProcessorFailureHandlingStrategy.class);

  @Override
  public void handleFailure(final FailureHandlingContext context) throws RuntimeException {
    LOG.trace(
        "Uploading documents {} for job {} failed, failing job.",
        context.response().getFailedDocuments(),
        context.activatedJob().getKey());
    throw new RuntimeException(
        String.format(
            "Uploading documents %s for job %s failed",
            context.response().getFailedDocuments(), context.activatedJob().getKey()));
  }
}
