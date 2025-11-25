/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {buildProcessInstanceFilter} from './processInstanceFilterBuilder';

import type {ProcessInstanceFilters} from 'modules/utils/filter/shared';
import type {RequestFilters} from 'modules/utils/filter';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';

const mockBusinessObjects: BusinessObjects = {
  serviceTask1: {
    id: 'serviceTask1',
    name: 'Service Task',
    $type: 'bpmn:ServiceTask',
  },
  endEvent1: {
    id: 'endEvent1',
    name: 'End Event',
    $type: 'bpmn:EndEvent',
    $parent: {
      id: 'process1',
      name: 'Process',
      $type: 'bpmn:Process',
    },
  },
  userTask1: {
    id: 'userTask1',
    name: 'User Task',
    $type: 'bpmn:UserTask',
  },
};

describe('buildProcessInstanceFilter', () => {
  it('maps fields with correct operators and date ranges', () => {
    const filters: ProcessInstanceFilters = {
      startDateAfter: '2023-01-01T00:00:00Z',
      startDateBefore: '2023-01-31T00:00:00Z',
      endDateAfter: '2023-02-01T00:00:00Z',
      endDateBefore: '2023-02-28T00:00:00Z',
      ids: 'id1,id2',
      parentInstanceId: 'parent1',
      operationId: 'op1',
      flowNodeId: 'node1',
      tenant: 'tenant1',
      retriesLeft: true,
      errorMessage: 'boom',
      incidentErrorHashCode: 123,
    };

    const result = buildProcessInstanceFilter(filters);

    expect(result).toEqual({
      processInstanceKey: {$in: ['id1', 'id2']},
      parentProcessInstanceKey: {$eq: 'parent1'},
      batchOperationId: {$eq: 'op1'},
      elementId: {$eq: 'node1'},
      tenantId: {$eq: 'tenant1'},
      hasRetriesLeft: true,
      errorMessage: {$in: ['boom']},
      incidentErrorHashCode: 123,
      startDate: {
        $gt: '2023-01-01T00:00:00.000Z',
        $lt: '2023-01-31T00:00:00.000Z',
      },
      endDate: {
        $gt: '2023-02-01T00:00:00.000Z',
        $lt: '2023-02-28T00:00:00.000Z',
      },
    });
  });

  it('emits hasIncident only when incidents=true and no states selected', () => {
    const filters: ProcessInstanceFilters = {incidents: true};
    const result = buildProcessInstanceFilter(filters);
    expect(result).toEqual({hasIncident: true});
  });

  it('uses $eq for a single selected state, $in for multiple states', () => {
    const singleStateFilter: ProcessInstanceFilters = {active: true};
    const multipleStateFilters: ProcessInstanceFilters = {
      completed: true,
      canceled: true,
    };

    expect(buildProcessInstanceFilter(singleStateFilter)).toEqual({
      state: {$eq: 'ACTIVE'},
    });

    expect(buildProcessInstanceFilter(multipleStateFilters)).toEqual({
      state: {$in: ['COMPLETED', 'TERMINATED']},
    });
  });

  it('injects processDefinitionKey from options', () => {
    const filters: ProcessInstanceFilters = {};
    const result = buildProcessInstanceFilter(filters, {
      processDefinitionKeys: ['p1', 'p2'],
    });

    expect(result).toEqual({
      processDefinitionKey: {$in: ['p1', 'p2']},
    });
  });

  it('parses and stringifies variable values from URL format', () => {
    const filters: ProcessInstanceFilters = {
      variableName: 'orderId',
      variableValues: '123,"test",true',
    };
    const result = buildProcessInstanceFilter(filters);

    expect(result).toEqual({
      variables: [
        {name: 'orderId', value: '123'},
        {name: 'orderId', value: '"test"'},
        {name: 'orderId', value: 'true'},
      ],
    });
  });

  it('maps RequestFilters fields with operators', () => {
    const filters: RequestFilters = {
      activityId: 'act-1',
      errorMessage: 'msg',
      tenantId: 't-1',
      batchOperationId: 'op-9',
      parentInstanceId: 'pi-parent',
      retriesLeft: true,
      incidentErrorHashCode: 111,
      processIds: ['pd1', 'pd2'],
      startDateAfter: '2020-01-01T00:00:00Z',
      endDateBefore: '2020-01-03T00:00:00Z',
    };

    const result = buildProcessInstanceFilter(filters);

    expect(result).toEqual({
      elementId: {$eq: 'act-1'},
      errorMessage: {$in: ['msg']},
      tenantId: {$eq: 't-1'},
      batchOperationId: {$eq: 'op-9'},
      parentProcessInstanceKey: {$eq: 'pi-parent'},
      hasRetriesLeft: true,
      incidentErrorHashCode: 111,
      processDefinitionKey: {$in: ['pd1', 'pd2']},
      startDate: {$gt: '2020-01-01T00:00:00.000Z'},
      endDate: {$lt: '2020-01-03T00:00:00.000Z'},
    });
  });

  it('passes variable values through as-is', () => {
    const filters: RequestFilters = {
      variable: {name: 'foo', values: ['a', 'b']},
    };
    const result = buildProcessInstanceFilter(filters);

    expect(result).toEqual({
      variables: [
        {name: 'foo', value: 'a'},
        {name: 'foo', value: 'b'},
      ],
    });
  });

  it('uses include/exclude ids from options', () => {
    const filters: RequestFilters = {};
    const result = buildProcessInstanceFilter(filters, {
      includeIds: ['1', '2'],
      excludeIds: ['x'],
    });

    expect(result).toEqual({
      processInstanceKey: {$in: ['1', '2'], $notIn: ['x']},
    });
  });

  it('does not add elementInstanceState filter when no element is selected', () => {
    const filters: ProcessInstanceFilters = {
      active: true,
    };

    const result = buildProcessInstanceFilter(filters, {
      businessObjects: mockBusinessObjects,
    });

    expect(result).toEqual({
      state: {$eq: 'ACTIVE'},
    });
    expect(result.elementInstanceState).toBeUndefined();
  });

  it('does not add elementInstanceState filter when element is an end event', () => {
    const filters: ProcessInstanceFilters = {
      flowNodeId: 'endEvent1',
      active: true,
    };

    const result = buildProcessInstanceFilter(filters, {
      businessObjects: mockBusinessObjects,
    });

    expect(result).toEqual({
      elementId: {$eq: 'endEvent1'},
      state: {$eq: 'ACTIVE'},
    });
    expect(result.elementInstanceState).toBeUndefined();
  });

  it('adds elementInstanceState filter when element is not an end event', () => {
    const filters: ProcessInstanceFilters = {
      flowNodeId: 'serviceTask1',
      active: true,
    };

    const result = buildProcessInstanceFilter(filters, {
      businessObjects: mockBusinessObjects,
    });

    expect(result).toEqual({
      elementId: {$eq: 'serviceTask1'},
      state: {$eq: 'ACTIVE'},
      elementInstanceState: {$neq: 'COMPLETED'},
    });
  });

  it('does not add version or processDefinitionVersionTag to the filter', () => {
    const filters: ProcessInstanceFilters = {
      version: 'v1',
      process: 'my-process',
    };

    const result = buildProcessInstanceFilter(filters);

    expect(result.processDefinitionVersionTag).toBeUndefined();
    expect(result.processDefinitionVersion).toBeUndefined();
  });
});
