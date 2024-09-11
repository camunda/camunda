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
package io.camunda.zeebe.model.bpmn.validation;

import static io.camunda.zeebe.model.bpmn.validation.ExpectedValidationResult.expect;
import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.singletonList;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskForm;
import java.util.Arrays;
import org.junit.runners.Parameterized.Parameters;

public class ZeebeTaskValidatorFormTest extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////// Job-based user tasks ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormKey("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeExternalFormReference("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId("")
            .zeebeFormKey("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId("")
            .zeebeExternalFormReference("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormKey("")
            .zeebeExternalFormReference("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId("")
            .zeebeFormKey("")
            .zeebeExternalFormReference("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId("form-id")
            .zeebeFormKey("form-key")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId("form-id")
            .zeebeExternalFormReference("reference")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormKey("form-key")
            .zeebeExternalFormReference("reference")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId("form-id")
            .zeebeFormKey("form-key")
            .zeebeExternalFormReference("reference")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId(" ")
            .zeebeFormKey("form-key")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId("form-id")
            .zeebeFormKey(" ")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId(" ")
            .zeebeExternalFormReference("reference")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId("form-id")
            .zeebeExternalFormReference(" ")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormKey(" ")
            .zeebeExternalFormReference("reference")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormKey("form-key")
            .zeebeExternalFormReference(" ")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId(" ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormKey(" ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeExternalFormReference(" ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId("  ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormKey("  ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeExternalFormReference("  ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTaskForm("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeUserTaskForm.class,
                "User task form text content has to be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormId("form-id")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeFormKey("form-key")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeExternalFormReference("reference")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, formKey' must be present and not blank"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeFormId("formId")
                        .getElement()
                        .getSingleExtensionElement(ZeebeFormDefinition.class)
                        .setAttributeValue(ZeebeConstants.ATTRIBUTE_BINDING_TYPE, "foo"))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'bindingType' must be one of: deployment, latest, versionTag"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeFormId("formId").zeebeFormBindingType(ZeebeBindingType.versionTag))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'versionTag' must be present and not empty if 'bindingType' is 'versionTag'"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeFormId("formId")
                        .zeebeFormBindingType(ZeebeBindingType.versionTag)
                        .zeebeFormVersionTag(""))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'versionTag' must be present and not empty if 'bindingType' is 'versionTag'"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeFormId("formId")
                        .zeebeFormBindingType(ZeebeBindingType.versionTag)
                        .zeebeFormVersionTag(" "))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'versionTag' must be present and not empty if 'bindingType' is 'versionTag'"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeFormId("formId")
                        .zeebeFormBindingType(ZeebeBindingType.deployment)
                        .zeebeFormVersionTag("v1.0"))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'versionTag' may only be used if 'bindingType' is 'versionTag'"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeFormId("formId")
                        .zeebeFormBindingType(ZeebeBindingType.latest)
                        .zeebeFormVersionTag("v1.0"))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'versionTag' may only be used if 'bindingType' is 'versionTag'"))
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////////// Native user tasks ////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormKey("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormId("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeExternalFormReference("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormId("")
            .zeebeFormKey("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormId("")
            .zeebeExternalFormReference("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormKey("")
            .zeebeExternalFormReference("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormId("")
            .zeebeFormKey("")
            .zeebeExternalFormReference("")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormId("form-id")
            .zeebeFormKey("form-key")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormId("form-id")
            .zeebeExternalFormReference("reference")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormKey("form-key")
            .zeebeExternalFormReference("reference")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormId("form-id")
            .zeebeFormKey("form-key")
            .zeebeExternalFormReference("reference")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormId(" ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormKey(" ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeExternalFormReference(" ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormId("  ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormKey("  ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeExternalFormReference("  ")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeUserTaskForm("")
            .endEvent()
            .done(),
        Arrays.asList(
            expect(
                ZeebeUserTaskForm.class,
                "User task form text content has to be present and not empty"),
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormId("form-id")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeFormKey("form-key")
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Exactly one of the attributes 'formId, externalReference' must be present and not blank for native user tasks"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeExternalFormReference("reference")
            .endEvent()
            .done(),
        EMPTY_LIST
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeUserTask()
                        .zeebeFormId("formId")
                        .getElement()
                        .getSingleExtensionElement(ZeebeFormDefinition.class)
                        .setAttributeValue(ZeebeConstants.ATTRIBUTE_BINDING_TYPE, "foo"))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'bindingType' must be one of: deployment, latest, versionTag"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeUserTask()
                        .zeebeFormId("formId")
                        .zeebeFormBindingType(ZeebeBindingType.versionTag))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'versionTag' must be present and not empty if 'bindingType' is 'versionTag'"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeUserTask()
                        .zeebeFormId("formId")
                        .zeebeFormBindingType(ZeebeBindingType.versionTag)
                        .zeebeFormVersionTag(""))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'versionTag' must be present and not empty if 'bindingType' is 'versionTag'"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeUserTask()
                        .zeebeFormId("formId")
                        .zeebeFormBindingType(ZeebeBindingType.versionTag)
                        .zeebeFormVersionTag(" "))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'versionTag' must be present and not empty if 'bindingType' is 'versionTag'"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeUserTask()
                        .zeebeFormId("formId")
                        .zeebeFormBindingType(ZeebeBindingType.deployment)
                        .zeebeFormVersionTag("v1.0"))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'versionTag' may only be used if 'bindingType' is 'versionTag'"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "task",
                task ->
                    task.zeebeUserTask()
                        .zeebeFormId("formId")
                        .zeebeFormBindingType(ZeebeBindingType.latest)
                        .zeebeFormVersionTag("v1.0"))
            .endEvent()
            .done(),
        singletonList(
            expect(
                ZeebeFormDefinition.class,
                "Attribute 'versionTag' may only be used if 'bindingType' is 'versionTag'"))
      },
    };
  }
}
