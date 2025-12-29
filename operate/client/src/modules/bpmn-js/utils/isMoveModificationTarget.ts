/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import isNil from 'lodash/isNil';
import {isAttachedToAnEventBasedGateway} from './isAttachedToAnEventBasedGateway';
import {hasType} from './hasType';
import {hasEventType} from './hasEventType';

const isMoveModificationTarget = (
  businessObject: BusinessObject | null | undefined,
) => {
  if (isNil(businessObject)) {
    return false;
  }

  // this is temporary until #40960 is implemented
  if (
    hasEventType({businessObject, types: ['bpmn:ConditionalEventDefinition']})
  ) {
    return false;
  }

  return (
    !isAttachedToAnEventBasedGateway(businessObject) &&
    !hasType({businessObject, types: ['bpmn:StartEvent', 'bpmn:BoundaryEvent']})
  );
};

export {isMoveModificationTarget};
