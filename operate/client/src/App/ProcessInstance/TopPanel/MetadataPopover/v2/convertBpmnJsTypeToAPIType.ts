/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ElementType} from 'bpmn-js/lib/NavigatedViewer';
import type {ElementInstance} from '@vzeta/camunda-api-zod-schemas';

const convertBpmnJsTypeToAPIType = (
  elementTypeName: ElementType | null | undefined,
): ElementInstance['type'] => {
  if (!elementTypeName) {
    return 'UNSPECIFIED';
  }

  return elementTypeName
    .replace(/^.*:/, '')
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .toUpperCase() as ElementInstance['type'];
};

export {convertBpmnJsTypeToAPIType};
