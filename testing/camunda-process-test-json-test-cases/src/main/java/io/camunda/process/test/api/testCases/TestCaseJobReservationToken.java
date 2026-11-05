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
package io.camunda.process.test.api.testCases;

/**
 * The placeholder a saved test case uses for a job reservation token.
 *
 * <p>A reservation token is a per-run secret and must not be saved with a test case. A test case
 * writes {@link #PLACEHOLDER} wherever a token is needed (the {@code RESERVE_JOBS} runtime
 * instruction and the job instructions that act on a reserved job), and the runner substitutes a
 * fresh token for it on each run.
 */
public final class TestCaseJobReservationToken {

  /** The literal value that stands for the token of the current run. */
  public static final String PLACEHOLDER = "${jobReservationToken}";

  private TestCaseJobReservationToken() {}
}
