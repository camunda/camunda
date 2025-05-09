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
package io.camunda.process.test.impl.spec.dsl;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import io.camunda.process.test.impl.spec.dsl.actions.CompleteJobAction;
import io.camunda.process.test.impl.spec.dsl.actions.CreateProcessInstanceAction;
import io.camunda.process.test.impl.spec.dsl.actions.IncreaseTimeAction;
import io.camunda.process.test.impl.spec.dsl.actions.PublishMessageAction;
import io.camunda.process.test.impl.spec.dsl.verifications.ElementInstanceStateVerification;
import io.camunda.process.test.impl.spec.dsl.verifications.ProcessInstanceStateVerification;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = As.EXISTING_PROPERTY,
    property = "name",
    visible = true)
@JsonSubTypes({
  // ------ Actions ------
  @JsonSubTypes.Type(value = CreateProcessInstanceAction.class, name = "create-process-instance"),
  @JsonSubTypes.Type(value = CompleteJobAction.class, name = "complete-job"),
  @JsonSubTypes.Type(value = PublishMessageAction.class, name = "publish-message"),
  @JsonSubTypes.Type(value = IncreaseTimeAction.class, name = "increase-time"),
  // ------ Verifications ------
  @JsonSubTypes.Type(
      value = ProcessInstanceStateVerification.class,
      name = "verify-process-instance-state"),
  @JsonSubTypes.Type(
      value = ElementInstanceStateVerification.class,
      name = "verify-element-instance-state")
})
public interface SpecInstruction {

  String getName();
}
