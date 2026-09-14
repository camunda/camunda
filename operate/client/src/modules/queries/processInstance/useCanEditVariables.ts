/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstance} from './useProcessInstance';
import {canEditVariables} from 'modules/utils/instance';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.10';

const useCanEditVariables = (
  elementType?: ElementInstance['type'],
  isElementTypeUnresolved = false,
) =>
  useProcessInstance<boolean>((processInstance) =>
    canEditVariables(processInstance, elementType, isElementTypeUnresolved),
  );

export {useCanEditVariables};
