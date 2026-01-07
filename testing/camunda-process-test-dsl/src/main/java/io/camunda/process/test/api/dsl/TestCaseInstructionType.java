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
package io.camunda.process.test.api.dsl;

/** A collection of supported test case instruction types. */
public class TestCaseInstructionType {

  public static final String ASSERT_ELEMENT_INSTANCE = "ASSERT_ELEMENT_INSTANCE";
  public static final String ASSERT_ELEMENT_INSTANCES = "ASSERT_ELEMENT_INSTANCES";
  public static final String ASSERT_PROCESS_INSTANCE = "ASSERT_PROCESS_INSTANCE";
  public static final String ASSERT_PROCESS_INSTANCE_MESSAGE_SUBSCRIPTION =
      "ASSERT_PROCESS_INSTANCE_MESSAGE_SUBSCRIPTION";
  public static final String ASSERT_USER_TASK = "ASSERT_USER_TASK";
  public static final String ASSERT_VARIABLES = "ASSERT_VARIABLES";
  public static final String COMPLETE_JOB = "COMPLETE_JOB";
  public static final String COMPLETE_USER_TASK = "COMPLETE_USER_TASK";
  public static final String CREATE_PROCESS_INSTANCE = "CREATE_PROCESS_INSTANCE";
  public static final String EVALUATE_CONDITIONAL_START_EVENT = "EVALUATE_CONDITIONAL_START_EVENT";
  public static final String MOCK_CHILD_PROCESS = "MOCK_CHILD_PROCESS";
  public static final String MOCK_DMN_DECISION = "MOCK_DMN_DECISION";
  public static final String MOCK_JOB_WORKER_COMPLETE_JOB = "MOCK_JOB_WORKER_COMPLETE_JOB";
  public static final String PUBLISH_MESSAGE = "PUBLISH_MESSAGE";
}
