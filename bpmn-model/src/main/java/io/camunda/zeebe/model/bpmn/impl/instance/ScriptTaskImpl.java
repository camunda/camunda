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

package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_SCRIPT_FORMAT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_SCRIPT_TASK;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ScriptTaskBuilder;
import io.zeebe.model.bpmn.instance.Script;
import io.zeebe.model.bpmn.instance.ScriptTask;
import io.zeebe.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN scriptTask element
 *
 * @author Sebastian Menski
 */
public class ScriptTaskImpl extends TaskImpl implements ScriptTask {

  protected static Attribute<String> scriptFormatAttribute;
  protected static ChildElement<Script> scriptChild;

  public ScriptTaskImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ScriptTask.class, BPMN_ELEMENT_SCRIPT_TASK)
            .namespaceUri(BPMN20_NS)
            .extendsType(Task.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ScriptTask>() {
                  @Override
                  public ScriptTask newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new ScriptTaskImpl(instanceContext);
                  }
                });

    scriptFormatAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_SCRIPT_FORMAT).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    scriptChild = sequenceBuilder.element(Script.class).build();

    typeBuilder.build();
  }

  @Override
  public ScriptTaskBuilder builder() {
    return new ScriptTaskBuilder((BpmnModelInstance) modelInstance, this);
  }

  @Override
  public String getScriptFormat() {
    return scriptFormatAttribute.getValue(this);
  }

  @Override
  public void setScriptFormat(final String scriptFormat) {
    scriptFormatAttribute.setValue(this, scriptFormat);
  }

  @Override
  public Script getScript() {
    return scriptChild.getChild(this);
  }

  @Override
  public void setScript(final Script script) {
    scriptChild.setChild(this, script);
  }
}
