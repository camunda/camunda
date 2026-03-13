/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

/**
 * Checks if a user task is a Camunda user task.
 *
 * @param businessObject - The BPMN business object to check
 * @returns true if the element is a Camunda user task
 */
const isCamundaUserTask = (businessObject?: BusinessObject): boolean => {
  if (businessObject?.$type !== 'bpmn:UserTask') {
    return false;
  }

  return (
    businessObject.extensionElements?.values?.some(
      (element) => element.$type === 'zeebe:userTask',
    ) ?? false
  );
};

export {isCamundaUserTask};
