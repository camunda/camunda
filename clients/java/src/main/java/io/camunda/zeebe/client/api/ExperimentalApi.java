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
package io.camunda.zeebe.client.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates a public API that can change at any time, and has no guarantee of API stability and
 * backward-compatibility. If users want stabilization or signature change of a specific API that is
 * currently annotated {@code @ExperimentalApi}, please comment on its tracking issue on github with
 * rationale, usecase, and so forth, so that the Zeebe team may prioritize the process toward
 * stabilization of the API.
 *
 * <p>Usage guidelines:
 *
 * <ol>
 *   <li>This annotation is used only on public API. Internal interfaces should not use it.
 *   <li>After Zeebe has gained API stability, this annotation can only be added to new API. Adding
 *       it to an existing API is considered API-breaking.
 *   <li>Removing this annotation from an API gives it stable status.
 * </ol>
 *
 * <p>Note: This annotation is intended only for Zeebe library code. Users should not attach this
 * annotation to their own code.
 *
 * <p>This annotation was originally copied from io.grpc.ExperimentalApi, licensed under the Apache
 * License, Version 2.0. Copyright 2015 The gRPC Authors. Changes have been made since.
 *
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.ExperimentalApi}
 */
@Deprecated
@Retention(RetentionPolicy.CLASS)
@Target({
  ElementType.ANNOTATION_TYPE,
  ElementType.CONSTRUCTOR,
  ElementType.FIELD,
  ElementType.METHOD,
  ElementType.PACKAGE,
  ElementType.TYPE
})
@Documented
public @interface ExperimentalApi {
  /** Context information such as links to discussion thread, tracking issue etc. */
  String value();
}
