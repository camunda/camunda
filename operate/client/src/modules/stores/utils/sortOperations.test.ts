/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {sortOperations} from './sortOperations';
import type {OperationEntity} from 'modules/types/operate';

const MOCK_RUNNING_OPERATION = {
  id: '8a2e3d79-b5ec-4cef-92cd-6ead2035b972',
  name: null,
  type: 'RESOLVE_INCIDENT',
  startDate: '2020-03-23T15:29:19.170+0100',
  endDate: null,
  instancesCount: 893,
  operationsTotalCount: 406,
  operationsFinishedCount: 0,
  sortValues: ['9223372036854775807', '1584973759170'],
} satisfies OperationEntity;
const MOCK_FINISHED_OPERATIONS_RESOLVE_INCIDENT = {
  id: '21ac5d59-cdf6-48cd-b467-00c0c8ffeeb3',
  name: null,
  type: 'RESOLVE_INCIDENT',
  startDate: '2020-03-23T11:46:37.960+0100',
  endDate: '2020-03-23T11:46:55.713+0100',
  instancesCount: 893,
  operationsTotalCount: 406,
  operationsFinishedCount: 406,
  sortValues: ['1584960415713', '1584960397960'],
} satisfies OperationEntity;
const MOCK_FINISHED_OPERATIONS_CANCEL_PROCESS_INSTANCE = {
  id: 'b22d5134-64de-4dbb-af9b-a211aaebed47',
  name: null,
  type: 'CANCEL_PROCESS_INSTANCE',
  startDate: '2020-03-20T19:31:09.478+0100',
  endDate: '2020-03-20T19:31:17.637+0100',
  instancesCount: 1,
  operationsTotalCount: 1,
  operationsFinishedCount: 1,
  sortValues: ['1584729077637', '1584729069478'],
} satisfies OperationEntity;

describe('sortOperations', () => {
  it('should put running operations first', () => {
    expect(
      sortOperations([
        MOCK_FINISHED_OPERATIONS_RESOLVE_INCIDENT,
        MOCK_FINISHED_OPERATIONS_CANCEL_PROCESS_INSTANCE,
        MOCK_RUNNING_OPERATION,
      ]),
    ).toEqual([
      MOCK_RUNNING_OPERATION,
      MOCK_FINISHED_OPERATIONS_RESOLVE_INCIDENT,
      MOCK_FINISHED_OPERATIONS_CANCEL_PROCESS_INSTANCE,
    ]);
  });

  it('should order finished operations by end date', () => {
    expect(
      sortOperations([
        MOCK_FINISHED_OPERATIONS_CANCEL_PROCESS_INSTANCE,
        MOCK_FINISHED_OPERATIONS_RESOLVE_INCIDENT,
      ]),
    ).toEqual([
      MOCK_FINISHED_OPERATIONS_RESOLVE_INCIDENT,
      MOCK_FINISHED_OPERATIONS_CANCEL_PROCESS_INSTANCE,
    ]);
  });
});
