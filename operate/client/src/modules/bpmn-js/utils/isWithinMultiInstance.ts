/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

const isWithinMultiInstance = (businessObject: BusinessObject) => {
  return (
    businessObject.$parent?.loopCharacteristics?.$type ===
    'bpmn:MultiInstanceLoopCharacteristics'
  );
};

const getFirstMultiInstanceParent = (
  businessObject?: BusinessObject,
): BusinessObject | undefined => {
  if (!businessObject) {
    return undefined;
  }

  if (isWithinMultiInstance(businessObject)) {
    return businessObject.$parent;
  }

  return getFirstMultiInstanceParent(businessObject.$parent);
};

export {isWithinMultiInstance, getFirstMultiInstanceParent};
