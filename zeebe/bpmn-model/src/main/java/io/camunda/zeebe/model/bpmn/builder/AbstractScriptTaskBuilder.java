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

package io.camunda.zeebe.model.bpmn.builder;

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Script;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeScript;

/**
 * @author Sebastian Menski
 */
public abstract class AbstractScriptTaskBuilder<B extends AbstractScriptTaskBuilder<B>>
    extends AbstractJobWorkerTaskBuilder<B, ScriptTask> {

  protected AbstractScriptTaskBuilder(
      final BpmnModelInstance modelInstance, final ScriptTask element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the script format of the build script task.
   *
   * @param scriptFormat the script format to set
   * @return the builder object
   */
  public B scriptFormat(final String scriptFormat) {
    element.setScriptFormat(scriptFormat);
    return myself;
  }

  /**
   * Sets the script of the build script task.
   *
   * @param script the script to set
   * @return the builder object
   */
  public B script(final Script script) {
    element.setScript(script);
    return myself;
  }

  public B scriptText(final String scriptText) {
    final Script script = createChild(Script.class);
    script.setTextContent(scriptText);
    return myself;
  }

  /**
   * Sets feel script text of the script task that is called
   *
   * @param expression the feel expression for the script task
   * @return the builder object
   */
  public B zeebeExpression(final String expression) {
    final ZeebeScript zeebeScript = getCreateSingleExtensionElement(ZeebeScript.class);
    zeebeScript.setExpression(asZeebeExpression(expression));
    return myself;
  }

  /**
   * Sets the name of the result variable.
   *
   * @param resultVariable the name of the result variable
   * @return the builder object
   */
  public B zeebeResultVariable(final String resultVariable) {
    final ZeebeScript zeebeScript = getCreateSingleExtensionElement(ZeebeScript.class);
    zeebeScript.setResultVariable(resultVariable);
    return myself;
  }
}
