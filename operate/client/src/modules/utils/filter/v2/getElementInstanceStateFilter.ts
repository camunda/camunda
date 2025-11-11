/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {isProcessOrSubProcessEndEvent} from 'modules/bpmn-js/utils/isProcessEndEvent';

type ElementInstanceStateFilter =
  | {
      $neq: 'COMPLETED';
    }
  | undefined;

const getElementInstanceStateFilter = (
  elementId: string | undefined,
  businessObjects: BusinessObjects | undefined,
): ElementInstanceStateFilter => {
  if (!elementId || !businessObjects) {
    return undefined;
  }

  const businessObject = businessObjects[elementId];

  if (!businessObject) {
    return undefined;
  }

  const isEndEvent = isProcessOrSubProcessEndEvent(businessObject);

  if (isEndEvent) {
    return undefined;
  }

  return {$neq: 'COMPLETED'};
};

export {getElementInstanceStateFilter};
