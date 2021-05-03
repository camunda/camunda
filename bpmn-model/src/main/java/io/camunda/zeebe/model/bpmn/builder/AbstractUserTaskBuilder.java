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

import static io.zeebe.model.bpmn.impl.ZeebeConstants.USER_TASK_FORM_KEY_BPMN_LOCATION;
import static io.zeebe.model.bpmn.impl.ZeebeConstants.USER_TASK_FORM_KEY_CAMUNDA_FORMS_FORMAT;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.UserTask;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskForm;

/** @author Sebastian Menski */
public abstract class AbstractUserTaskBuilder<B extends AbstractUserTaskBuilder<B>>
    extends AbstractTaskBuilder<B, UserTask> {

  protected AbstractUserTaskBuilder(
      final BpmnModelInstance modelInstance, final UserTask element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the implementation of the build user task.
   *
   * @param implementation the implementation to set
   * @return the builder object
   */
  public B implementation(final String implementation) {
    element.setImplementation(implementation);
    return myself;
  }

  /** camunda extensions */

  /**
   * Sets the form key with the format 'format:location:id' of the build user task.
   *
   * @param format the format of the reference form
   * @param location the location where the form is available
   * @param id the id of the form
   * @return the builder object
   */
  public B zeebeFormKey(final String format, final String location, final String id) {
    return zeebeFormKey(String.format("%s:%s:%s", format, location, id));
  }

  /**
   * Sets the form key of the build user task.
   *
   * @param formKey the form key to set
   * @return the builder object
   */
  public B zeebeFormKey(final String formKey) {
    final ZeebeFormDefinition formDefinition =
        getCreateSingleExtensionElement(ZeebeFormDefinition.class);
    formDefinition.setFormKey(formKey);
    return myself;
  }

  /**
   * Creates an new user task form with the given context, assuming it is of the format
   * camunda-forms and embedded inside the diagram.
   *
   * @param userTaskForm the XML encoded user task form json in the camunda-forms format
   * @return the builder object
   */
  public B zeebeUserTaskForm(final String userTaskForm) {
    final ZeebeUserTaskForm zeebeUserTaskForm = createZeebeUserTaskForm();
    zeebeUserTaskForm.setTextContent(userTaskForm);
    return zeebeFormKey(
        USER_TASK_FORM_KEY_CAMUNDA_FORMS_FORMAT,
        USER_TASK_FORM_KEY_BPMN_LOCATION,
        zeebeUserTaskForm.getId());
  }

  /**
   * Creates an new user task form with the given context, assuming it is of the format
   * camunda-forms and embedded inside the diagram.
   *
   * @param id the unique identifier of the user task form element
   * @param userTaskForm the XML encoded user task form json in the camunda-forms format
   * @return the builder object
   */
  public B zeebeUserTaskForm(final String id, final String userTaskForm) {
    final ZeebeUserTaskForm zeebeUserTaskForm = createZeebeUserTaskForm();
    zeebeUserTaskForm.setId(id);
    zeebeUserTaskForm.setTextContent(userTaskForm);
    return zeebeFormKey(
        USER_TASK_FORM_KEY_CAMUNDA_FORMS_FORMAT, USER_TASK_FORM_KEY_BPMN_LOCATION, id);
  }

  public B zeebeTaskHeader(final String key, final String value) {
    final ZeebeTaskHeaders taskHeaders = getCreateSingleExtensionElement(ZeebeTaskHeaders.class);
    final ZeebeHeader header = createChild(taskHeaders, ZeebeHeader.class);
    header.setKey(key);
    header.setValue(value);

    return myself;
  }
}
