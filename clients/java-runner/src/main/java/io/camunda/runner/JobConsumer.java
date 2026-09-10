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
package io.camunda.runner;

import java.util.function.Consumer;

/**
 * Marker functional interface that exists solely to give the {@code Consumer<Job>} lambda overload
 * a distinct erasure from the existing {@code Consumer<ServiceTaskBuilder>} / {@code
 * Consumer<UserTaskBuilder>} pass-through overloads. Users do not refer to this type explicitly —
 * lambdas typed as {@code (Job job) -> ...} target it via overload resolution.
 */
@FunctionalInterface
public interface JobConsumer extends Consumer<Job> {}
