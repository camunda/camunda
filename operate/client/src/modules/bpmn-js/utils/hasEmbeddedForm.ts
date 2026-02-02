/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import type {ParsedXmlData} from 'modules/queries/processDefinitions/useProcessDefinitionXml';

/**
 * Checks if a user task has an embedded form (formKey) without being a Zeebe user task.
 * Job-based user tasks have a zeebe:formKey attribute but no zeebe:userTask element.
 *
 * @param businessObject - The BPMN business object to check
 * @returns true if the element is a job-based user task with an embedded form
 */
const hasEmbeddedForm = (businessObject?: BusinessObject): boolean => {
  // Check if it's a user task
  if (businessObject?.$type !== 'bpmn:UserTask') {
    return false;
  }

  // Check if it has a zeebe:userTask extension element (Zeebe user task)
  const hasZeebeUserTask =
    businessObject.extensionElements?.values?.some(
      (element) => element.$type === 'zeebe:userTask',
    ) ?? false;

  // If it has zeebe:userTask, it's a Zeebe user task, not job-based
  if (hasZeebeUserTask) {
    return false;
  }

  // Check if it has a zeebe:formKey attribute (embedded form)
  const formDefinition = businessObject.extensionElements?.values?.find(
    (element) => element.$type === 'zeebe:formDefinition',
  ) as {formKey?: string} | undefined;

  // It's a job-based user task with embedded form if it has formKey but no zeebe:userTask
  return formDefinition?.formKey !== undefined;
};

/**
 * Checks if a process has a user task with embedded form using the {@link hasEmbeddedForm} method.
 *
 * @param process - The parsed XML data to check
 * @returns true if the element is a job-based user task with an embedded form
 */
const hasProcessEmbeddedForm = (process?: ParsedXmlData): boolean => {
  if (process?.selectableFlowNodes) {
    for (const node of process.selectableFlowNodes) {
      if (hasEmbeddedForm(node)) {
        return true;
      }
    }
  }
  return false;
};

export {hasEmbeddedForm, hasProcessEmbeddedForm};
