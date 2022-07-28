/*
 * Copyright © 2022 camunda services GmbH (info@camunda.com)
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
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.response.ModifyProcessInstanceResponse;
import java.io.InputStream;
import java.util.Map;

public interface ModifyProcessInstanceCommandStep1 {

  ModifyProcessInstanceCommandStep2 activateElement(final String elementId);

  ModifyProcessInstanceCommandStep2 activateElement(
      final String elementId, final long ancestorElementInstanceKey);

  /**
   * Create a {@link
   * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.TerminateInstruction}
   * for the given element id.
   *
   * @param elementInstanceKey the element instance key of the element to termiante
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep2 terminateElement(final long elementInstanceKey);

  interface ModifyProcessInstanceCommandStep2
      extends FinalCommandStep<ModifyProcessInstanceResponse> {

    ModifyProcessInstanceCommandStep2 withVariables(final InputStream variables);

    ModifyProcessInstanceCommandStep2 withVariables(
        final InputStream variables, final String scopeId);

    ModifyProcessInstanceCommandStep2 withVariables(final String variables);

    ModifyProcessInstanceCommandStep2 withVariables(final String variables, final String scopeId);

    ModifyProcessInstanceCommandStep2 withVariables(final Map<String, Object> variables);

    ModifyProcessInstanceCommandStep2 withVariables(
        final Map<String, Object> variables, final String scopeId);

    ModifyProcessInstanceCommandStep2 withVariables(final Object variables);

    ModifyProcessInstanceCommandStep2 withVariables(final Object variables, final String scopeId);
  }

  interface ModifyProcessInstanceCommandStep3 {
    ModifyProcessInstanceCommandStep1 and();
  }
}
