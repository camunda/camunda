/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {gql} from '@apollo/client';
import {Process, ProcessInstance} from 'modules/types';

interface StartProcess {
  startProcess: Pick<Process, '__typename' | 'id'>;
}

interface StartProcessVariables {
  processDefinitionId: ProcessInstance['id'];
}

const START_PROCESS = gql`
  mutation StartProcess($processDefinitionId: String!) {
    startProcess(processDefinitionId: $processDefinitionId) {
      id
    }
  }
`;

export {START_PROCESS};
export type {StartProcess, StartProcessVariables};
