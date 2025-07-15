/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {hasType} from './hasType';
import {isEventSubProcess} from './isEventSubProcess';

const getEventSubProcessType = ({
  businessObject,
}: {
  businessObject: BusinessObject;
}) => {
  if (isEventSubProcess({businessObject})) {
    const startEvent = businessObject.flowElements?.find((businessObject) => {
      return hasType({businessObject, types: ['bpmn:StartEvent']});
    });

    return startEvent?.eventDefinitions?.[0]?.$type;
  }
};

export {getEventSubProcessType};
