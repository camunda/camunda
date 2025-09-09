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
package io.camunda.sdk.annotation.customizer;

import io.camunda.sdk.annotation.JobWorker;
import io.camunda.sdk.annotation.value.JobWorkerValue;

/**
 * This interface could be used to customize the {@link JobWorker} annotation's values. Register a
 * bean to make it work. But be careful: these customizers are applied sequentially and if you need
 * to change the order of these customizers use the bean ordering mechanism provided by the
 * enterprise framework you are using.
 */
public interface JobWorkerValueCustomizer {

  void customize(final JobWorkerValue jobWorkerValue);
}
