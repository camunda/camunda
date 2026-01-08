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
package io.camunda.client.api.command;

import io.camunda.client.api.response.ModifyProcessInstanceResponse;
import java.io.InputStream;
import java.util.Map;

public interface ModifyProcessInstanceCommandStep1
    extends CommandWithCommunicationApiStep<ModifyProcessInstanceCommandStep1> {

  /**
   * Create an activate instruction for the given element id.
   *
   * <p>The element will be created within an existing element instance of the flow scope. When
   * activating an element inside a multi-instance element the element instance key of the ancestor
   * must be defined. For this use {@link #activateElement(String, long)}.
   *
   * @param elementId the id of the element to activate
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 activateElement(final String elementId);

  /**
   * Create an activate instruction for the given element id.
   *
   * <p>The element will be created within the scope that is passed. This scope must be an ancestor
   * of the element that's getting activated.
   *
   * @param elementId the id of the element to activate
   * @param ancestorElementInstanceKey the element instance key in which the element will be created
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 activateElement(
      final String elementId, final long ancestorElementInstanceKey);

  /**
   * Create a terminate instruction for the given element instance key.
   *
   * @param elementInstanceKey the element instance key of the element to terminate
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep2 terminateElement(final long elementInstanceKey);

  /**
   * Create a terminate instruction for the given element id. The element instances will be
   * determined at runtime.
   *
   * @param elementId the id of the elements to terminate
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep2 terminateElements(final String elementId);

  /**
   * Create a move instruction for the given element ids. The element instances will be determined
   * at runtime. All source element instances will be terminated and will activate a new one at the
   * target element id. For multi-instance elements, only the body element will activate a new
   * element instance.
   *
   * <p>The elements will be created within an existing element instance of the flow scope. When
   * activating elements inside a multi-instance element, the element instance key of the ancestor
   * must be defined. For this, use {@link #moveElements(String, String, long)} or {@link
   * #moveElementsWithInferredAncestor(String, String)}.
   *
   * @param sourceElementId the id of the elements to move
   * @param targetElementId the id of target element to move to
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 moveElements(
      final String sourceElementId, final String targetElementId);

  /**
   * Create a move instruction for the given element ids. The element instances will be determined
   * at runtime. All source element instances will be terminated and will activate a new one at the
   * target element id. For multi-instance elements, only the body element will activate a new
   * element instance.
   *
   * <p>The elements will be created within the scope that is passed. This scope must be an ancestor
   * of the elements that are getting activated.
   *
   * @param sourceElementId the id of the elements to move
   * @param targetElementId the id of target element to move to
   * @param ancestorElementInstanceKey the element instance key in which the elements will be
   *     created
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 moveElements(
      final String sourceElementId,
      final String targetElementId,
      final long ancestorElementInstanceKey);

  /**
   * Create a move instruction for the given element ids. The element instances will be determined
   * at runtime. All source element instances will be terminated and will activate a new one at the
   * target element id. For multi-instance elements, only the body element will activate a new
   * element instance.
   *
   * <p>This instructs the engine to derive the ancestor scope keys from the source element's
   * hierarchy. The engine traverses the source element's ancestry to find an instance that matches
   * one of the target element's flow scopes, ensuring the target is activated in the correct scope.
   *
   * @param sourceElementId the id of the elements to move
   * @param targetElementId the id of target element to move to
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 moveElementsWithInferredAncestor(
      final String sourceElementId, final String targetElementId);

  /**
   * Create a move instruction for the given element ids. The element instances will be determined
   * at runtime. All source element instances will be terminated and will activate a new one at the
   * target element id. For multi-instance elements, only the body element will activate a new
   * element instance.
   *
   * <p>This instructs the engine to use the source's direct parent key as the ancestor scope key
   * for the target element. This is a simpler alternative to {@link
   * #moveElementsWithInferredAncestor(String, String)} that skips hierarchy traversal and directly
   * uses the source's parent key. This is useful when source and target elements are siblings
   * within the same flow scope.
   *
   * @param sourceElementId the id of the elements to move
   * @param targetElementId the id of target element to move to
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 moveElementsWithSourceParentAsAncestor(
      final String sourceElementId, final String targetElementId);

  /**
   * Create a move instruction for the given element instance. This source element instance will be
   * terminated and will activate a new one at the target element id. For multi-instance elements,
   * only the body element will activate a new element instance.
   *
   * <p>The target element will be created within an existing element instance of the flow scope.
   * When activating elements inside a multi-instance element, the element instance key of the
   * ancestor must be defined. For this, use {@link #moveElement(long, String, long)} or {@link
   * #moveElementWithInferredAncestor(long, String)}.
   *
   * @param sourceElementInstanceKey the key of the element to move
   * @param targetElementId the id of target element to move to
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 moveElement(
      final long sourceElementInstanceKey, final String targetElementId);

  /**
   * Create a move instruction for the given element instance. the source element instance with
   * given key will be terminated and will activate a new one at the target element id. For
   * multi-instance elements, only the body element will activate a new element instance.
   *
   * <p>The target element will be created within the scope that is passed. This scope must be an
   * ancestor of the elements that are getting activated.
   *
   * @param sourceElementInstanceKey the key of the element to move
   * @param targetElementId the id of target element to move to
   * @param ancestorElementInstanceKey the element instance key in which the elements will be
   *     created
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 moveElement(
      final long sourceElementInstanceKey,
      final String targetElementId,
      final long ancestorElementInstanceKey);

  /**
   * Create a move instruction for the given element instance. The source element instance with *
   * given key will be terminated and will activate a new one at the target element id. For
   * multi-instance elements, only the body element will activate a new element instance.
   *
   * <p>This instructs the engine to derive the ancestor scope key from the source element's
   * hierarchy. The engine traverses the source element's ancestry to find an instance that matches
   * one of the target element's flow scopes, ensuring the target is activated in the correct scope.
   *
   * @param sourceElementInstanceKey the key of the element to move
   * @param targetElementId the id of target element to move to
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 moveElementWithInferredAncestor(
      final long sourceElementInstanceKey, final String targetElementId);

  /**
   * Create a move instruction for the given element instance. The source element instance with
   * given key will be terminated and will activate a new one at the target element id. For
   * multi-instance elements, only the body element will activate a new element instance.
   *
   * <p>This instructs the engine to use the source's direct parent key as the ancestor scope key
   * for the target element. This is a simpler alternative to {@link
   * #moveElementWithInferredAncestor(long, String)} that skips hierarchy traversal and directly
   * uses the source's parent key. This is useful when source and target elements are siblings
   * within the same flow scope.
   *
   * @param sourceElementInstanceKey the key of the element to move
   * @param targetElementId the id of target element to move to
   * @return the builder for this command
   */
  ModifyProcessInstanceCommandStep3 moveElementWithSourceParentAsAncestor(
      final long sourceElementInstanceKey, final String targetElementId);

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
