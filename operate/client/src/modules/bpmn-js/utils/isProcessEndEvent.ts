/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {hasType} from './hasType';

const isProcessEndEvent = (businessObject: BusinessObject) => {
  return (
    hasType({businessObject, types: ['bpmn:EndEvent']}) &&
    businessObject?.$parent !== undefined &&
    hasType({businessObject: businessObject?.$parent, types: ['bpmn:Process']})
  );
};

export {isProcessEndEvent};
