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
package io.camunda.zeebe.client.api.command;

import io.camunda.zeebe.client.api.response.ModifyProcessInstanceResponse;
import java.io.InputStream;
import java.util.Map;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.command.ModifyProcessInstanceCommandStep1}
 */
@Deprecated
public interface ModifyProcessInstanceCommandStep1
    extends CommandWithCommunicationApiStep<ModifyProcessInstanceCommandStep1> {

  /**
   * Create an {@link
   * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.ActivateInstruction}
   * for the given element id. The element will be created within an existing element instance of
   * the flow scope. When activating an element inside a multi-instance element the element instance
   * key of the ancestor must be defined. For this use {@link #activateElement(String, long)}.
   *
   * @param elementId the id of the element to activate
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 activateElement(final String elementId);

  /**
   * Create an {@link
   * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.ActivateInstruction}
   * for the given element id. The element will be created within the scope that is passed. This
   * scope must be an ancestor of the element that's getting activated.
   *
   * @param elementId the id of the element to activate
   * @param ancestorElementInstanceKey the element instance key in which the element will be created
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 activateElement(
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

  /**
   * @deprecated since 8.8 for removal in 8.9, replaced by {@link
   *     io.camunda.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2}
   */
  @Deprecated
  interface ModifyProcessInstanceCommandStep2
      extends CommandWithOperationReferenceStep<ModifyProcessInstanceCommandStep2>,
          FinalCommandStep<ModifyProcessInstanceResponse> {
    /**
     * Acts as a boundary between the different activate and terminate instructions. Use this if you
     * want to activate or terminate another element. Otherwise, {@link #send()} the command.
     *
     * @return the builder for this command
     */
    ModifyProcessInstanceCommandStep1 and();
  }

  /**
   * @deprecated since 8.8 for removal in 8.9, replaced by {@link
   *     io.camunda.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3}
   */
  @Deprecated
  interface ModifyProcessInstanceCommandStep3 extends ModifyProcessInstanceCommandStep2 {

    /**
     * Create a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction}
     * for the element that's getting activated. These variables will be created in the global scope
     * of the process instance.
     *
     * @param variables the variables JSON document as stream
     * @return the builder for this command
     */
    ModifyProcessInstanceCommandStep3 withVariables(final InputStream variables);

    /**
     * Create a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction}
     * for the element that's getting activated. These variables will be created in the scope of the
     * passed element.
     *
     * @param variables the variables JSON document as stream
     * @param scopeId the id of the element in which scope the variables should be created
     * @return the builder for this command
     */
    ModifyProcessInstanceCommandStep3 withVariables(
        final InputStream variables, final String scopeId);

    /**
     * Create a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction}
     * for the element that's getting activated. These variables will be created in the global scope
     * of the process instance.
     *
     * @param variables the variables JSON document as String
     * @return the builder for this command
     */
    ModifyProcessInstanceCommandStep3 withVariables(final String variables);

    /**
     * Create a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction}
     * for the element that's getting activated. These variables will be created in the scope of the
     * passed element.
     *
     * @param variables the variables JSON document as String
     * @param scopeId the id of the element in which scope the variables should be created
     * @return the builder for this command
     */
    ModifyProcessInstanceCommandStep3 withVariables(final String variables, final String scopeId);

    /**
     * Create a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction}
     * for the element that's getting activated. These variables will be created in the global scope
     * of the process instance.
     *
     * @param variables the variables JSON document as map
     * @return the builder for this command
     */
    ModifyProcessInstanceCommandStep3 withVariables(final Map<String, Object> variables);

    /**
     * Create a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction}
     * for the element that's getting activated. These variables will be created in the scope of the
     * passed element.
     *
     * @param variables the variables JSON document as map
     * @param scopeId the id of the element in which scope the variables should be created
     * @return the builder for this command
     */
    ModifyProcessInstanceCommandStep3 withVariables(
        final Map<String, Object> variables, final String scopeId);

    /**
     * Create a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction}
     * for the element that's getting activated. These variables will be created in the global scope
     * of the process instance.
     *
     * @param variables the variables document as object to be serialized to JSON
     * @return the builder for this command
     */
    ModifyProcessInstanceCommandStep3 withVariables(final Object variables);

    /**
     * Create a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction}
     * for the element that's getting activated. These variables will be created in the scope of the
     * passed element.
     *
     * @param variables the variables document as object to be serialized to JSON
     * @param scopeId the id of the element in which scope the variables should be created
     * @return the builder for this command
     */
    ModifyProcessInstanceCommandStep3 withVariables(final Object variables, final String scopeId);

    /**
     * Create a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction}
     * for the element that's getting activated. This variable will be created in the global scope
     * of the process instance.
     *
     * @param key the key of the variable to be serialized to JSON
     * @param value the value of the variable to be serialized to JSON
     * @return the builder for this command
     */
    ModifyProcessInstanceCommandStep3 withVariable(final String key, final Object value);

    /**
     * Create a {@link
     * io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction}
     * for the element that's getting activated. This variable will be created in the scope of the
     * passed element.
     *
     * @param key the key of the variable to be serialized to JSON
     * @param value the value of the variable to be serialized to JSON
     * @param scopeId the id of the element in which scope the variable should be created
     * @return the builder for this command
     */
    ModifyProcessInstanceCommandStep3 withVariable(
        final String key, final Object value, final String scopeId);
  }
}
