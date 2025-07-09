/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type GetProcessInstanceCallHierarchyResponseBody} from '@vzeta/camunda-api-zod-schemas';
import {useCallHierarchy} from './useCallHierarchy';

const rootInstanceIdParser = (
  data: GetProcessInstanceCallHierarchyResponseBody,
) => {
  return data[0]?.processInstanceKey;
};

type useRootInstanceIdOptions = {
  enabled?: boolean;
};

const useRootInstanceId = (options?: useRootInstanceIdOptions) =>
  useCallHierarchy<string | undefined>(rootInstanceIdParser, options?.enabled);

export {useRootInstanceId};
