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
package io.zeebe.gateway.cmd;

import io.grpc.Status;

public class InvalidBrokerRequestArgumentException extends ClientException
    implements GrpcStatusException {

  private static final String MESSAGE_FORMAT = "Expected argument '%s' to be %s, but was %s";
  private static final long serialVersionUID = -1582037715962211105L;
  private final String argument;
  private final String expectedValue;
  private final String actualValue;

  public InvalidBrokerRequestArgumentException(
      String argument, String expectedValue, String actualValue) {
    this(argument, expectedValue, actualValue, null);
  }

  public InvalidBrokerRequestArgumentException(
      String argument, String expectedValue, String actualValue, Throwable cause) {
    super(String.format(MESSAGE_FORMAT, argument, expectedValue, actualValue), cause);

    this.argument = argument;
    this.expectedValue = expectedValue;
    this.actualValue = actualValue;
  }

  public String getArgument() {
    return argument;
  }

  public String getExpectedValue() {
    return expectedValue;
  }

  public String getActualValue() {
    return actualValue;
  }

  @Override
  public Status getGrpcStatus() {
    return Status.INVALID_ARGUMENT.augmentDescription(getMessage());
  }
}
