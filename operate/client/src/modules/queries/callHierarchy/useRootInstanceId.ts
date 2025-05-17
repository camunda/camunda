/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {GetProcessInstanceCallHierarchyResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {useCallHierarchy} from './useCallHierarchy';

const rootInstanceIdParser = (
  data: GetProcessInstanceCallHierarchyResponseBody,
) => {
  return data[0]?.processInstanceKey;
};

const useRootInstanceId = () =>
  useCallHierarchy<string | undefined>(rootInstanceIdParser);

export {useRootInstanceId};
