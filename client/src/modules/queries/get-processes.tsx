/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {gql} from '@apollo/client';
import {Process} from 'modules/types';

interface GetProcesses {
  processes: ReadonlyArray<Process>;
}

interface GetProcessesVariables {
  search?: string;
}

const GET_PROCESSES = gql`
  query GetProcesses($search: String) {
    processes(search: $search) {
      id
      name
      processDefinitionId
    }
  }
`;

function createMockProcess(id: string): Process {
  return {
    id,
    name: `Process ${id}`,
    processDefinitionId: `definition-id-${id}`,
    __typename: 'Process',
  };
}

export type {GetProcesses, GetProcessesVariables};
export {GET_PROCESSES, createMockProcess};
