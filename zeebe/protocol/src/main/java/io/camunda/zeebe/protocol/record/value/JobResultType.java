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
package io.camunda.zeebe.protocol.record.value;

/**
 * Enumerates the different supported types of job results. Depending on this type different
 * properties of the {@link io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultValue}
 * will be set.
 */
public enum JobResultType {
  /** Represents a user task job result type. */
  USER_TASK("userTask"),
  /** Represents an ad-hoc subprocess job result type. */
  AD_HOC_SUB_PROCESS("adHocSubProcess");

  final String type;

  JobResultType(final String type) {
    this.type = type;
  }

  public static JobResultType from(final String resultType) {
    // If resultType is null or empty we will default to USER_TASK for backward compatibility.
    if (resultType == null || resultType.isEmpty()) {
      return USER_TASK;
    }

    for (final JobResultType type : values()) {
      if (type.type.equals(resultType)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown job result type: " + resultType);
  }
}
