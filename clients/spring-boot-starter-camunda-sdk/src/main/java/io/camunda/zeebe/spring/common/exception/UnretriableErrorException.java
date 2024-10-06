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
package io.camunda.zeebe.spring.common.exception;

/**
 * Exception that can be thrown by a JobWorker if something goes wrong and the job should
 * not be retried (even if a retry is configured in the process model).
 */
public class UnretriableErrorException extends SdkException {

  public UnretriableErrorException(final String message) {
    super(message);
  }

  public UnretriableErrorException(final String message, final Throwable cause) {
    super(message, cause);
  }
}