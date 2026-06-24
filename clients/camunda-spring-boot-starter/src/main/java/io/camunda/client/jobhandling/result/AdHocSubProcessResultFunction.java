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
package io.camunda.client.jobhandling.result;

import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.command.CompleteJobResult;
import java.util.function.Function;

/**
 * This interface allows to describe the result that should be applied to a job that processes an
 * ad-hoc subprocess.
 */
public interface AdHocSubProcessResultFunction
    extends Function<CompleteAdHocSubProcessResultStep1, CompleteJobResult> {

  default Object getVariables() {
    return null;
  }

  static AdHocSubProcessResultFunction withVariables(
      final Object variables, final AdHocSubProcessResultFunction adHocSubProcessResultFunction) {
    return new AdHocSubProcessResultFunction() {
      @Override
      public CompleteJobResult apply(
          final CompleteAdHocSubProcessResultStep1 completeAdHocSubProcessResultStep1) {
        return adHocSubProcessResultFunction.apply(completeAdHocSubProcessResultStep1);
      }

      @Override
      public Object getVariables() {
        return variables;
      }
    };
  }
}
