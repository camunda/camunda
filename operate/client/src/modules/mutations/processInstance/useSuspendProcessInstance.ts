/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {suspendProcessInstance} from 'modules/api/v2/processInstances/suspendProcessInstance';
import {
  useChangeProcessInstanceState,
  type StateChangeError,
} from './useChangeProcessInstanceState';

type Options = {
  onSuccess?: () => void;
  onError?: (error: StateChangeError) => void;
};

const useSuspendProcessInstance = (
  processInstanceKey: string,
  options?: Options,
) =>
  useChangeProcessInstanceState(
    processInstanceKey,
    'SUSPENDED',
    suspendProcessInstance,
    options,
  );

export {useSuspendProcessInstance};
