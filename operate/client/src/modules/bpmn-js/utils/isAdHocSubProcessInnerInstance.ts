/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {hasType} from './hasType';

function isAdHocSubProcessInnerInstance(businessObject?: BusinessObject) {
  if (businessObject === undefined) {
    return false;
  }

  if (!hasType({businessObject, types: ['bpmn:AdHocSubProcess']})) {
    return false;
  }

  const extensionElement = businessObject.extensionElements?.values.find(
    (value) =>
      value?.$type === 'zeebe:taskDefinition' &&
      value?.type?.startsWith('io.camunda.agenticai:aiagent-job-worker'),
  );

  return extensionElement !== undefined;
}

export {isAdHocSubProcessInnerInstance};
