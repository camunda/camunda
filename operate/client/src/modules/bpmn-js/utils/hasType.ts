/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  type BusinessObject,
  type ElementType,
} from 'bpmn-js/lib/NavigatedViewer';

const hasType = ({
  businessObject,
  types,
}: {
  businessObject: BusinessObject;
  types: ElementType[];
}) => {
  if (types.includes(businessObject.$type)) {
    return true;
  }
};

export {hasType};
