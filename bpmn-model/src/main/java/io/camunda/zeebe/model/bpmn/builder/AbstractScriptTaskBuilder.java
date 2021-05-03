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

package io.zeebe.model.bpmn.builder;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.Script;
import io.zeebe.model.bpmn.instance.ScriptTask;

/** @author Sebastian Menski */
public abstract class AbstractScriptTaskBuilder<B extends AbstractScriptTaskBuilder<B>>
    extends AbstractTaskBuilder<B, ScriptTask> {

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
}
