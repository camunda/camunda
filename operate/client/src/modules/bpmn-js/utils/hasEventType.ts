/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject, EventType} from 'bpmn-js/lib/NavigatedViewer';
import {getEventType} from './getEventType';

const hasEventType = ({
  businessObject,
  types,
}: {
  businessObject: BusinessObject;
  types: EventType[];
}) => {
  const eventType = getEventType(businessObject);
  if (eventType !== undefined && types.includes(eventType)) {
    return true;
  }
};
export {hasEventType};
