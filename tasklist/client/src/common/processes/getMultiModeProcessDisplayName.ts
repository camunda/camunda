/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getProcessDisplayName} from 'v1/utils/getProcessDisplayName';
import type {MultiModeProcess} from './index';

function getMultiModeProcessDisplayName(process: MultiModeProcess) {
  if ('bpmnProcessId' in process) {
    return getProcessDisplayName(process);
  }

  return process.name ?? process.processDefinitionId;
}

export {getMultiModeProcessDisplayName};
