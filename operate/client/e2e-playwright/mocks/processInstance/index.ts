/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Route} from '@playwright/test';
import {
  FlowNodeInstanceDto,
  FlowNodeInstancesDto,
} from 'modules/api/fetchFlowNodeInstances';
import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import {ProcessInstanceDetailStatisticsDto} from 'modules/api/processInstances/fetchProcessInstanceDetailStatistics';
import {ProcessInstanceIncidentsDto} from 'modules/api/processInstances/fetchProcessInstanceIncidents';
import {SequenceFlowsDto} from 'modules/api/processInstances/sequenceFlows';

type InstanceMock = {
  xml: string;
  detail: ProcessInstanceEntity;
  flowNodeInstances: FlowNodeInstancesDto<FlowNodeInstanceDto>;
  statistics: ProcessInstanceDetailStatisticsDto[];
  sequenceFlows: SequenceFlowsDto;
  variables: VariableEntity[];
  incidents?: ProcessInstanceIncidentsDto;
  metaData?: MetaDataDto;
};

function mockResponses({
  processInstanceDetail,
  flowNodeInstances,
  statistics,
  sequenceFlows,
  variables,
  xml,
  incidents,
  metaData,
}: {
  processInstanceDetail?: ProcessInstanceEntity;
  flowNodeInstances?: FlowNodeInstancesDto<FlowNodeInstanceDto>;
  statistics?: ProcessInstanceDetailStatisticsDto[];
  sequenceFlows?: SequenceFlowsDto;
  variables?: VariableEntity[];
  xml?: string;
  incidents?: ProcessInstanceIncidentsDto;
  metaData?: MetaDataDto;
}) {
  return (route: Route) => {
    if (route.request().url().includes('/v2/authentication/me')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          userId: 'demo',
          displayName: 'demo',
          canLogout: true,
          roles: null,
          salesPlanType: null,
          c8Links: {},
          username: 'demo',
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/flow-node-instances')) {
      return route.fulfill({
        status: flowNodeInstances === undefined ? 400 : 200,
        body: JSON.stringify(flowNodeInstances),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('statistics')) {
      return route.fulfill({
        status: statistics === undefined ? 400 : 200,
        body: JSON.stringify(statistics),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('sequence-flows')) {
      return route.fulfill({
        status: sequenceFlows === undefined ? 400 : 200,
        body: JSON.stringify(sequenceFlows),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('variables')) {
      return route.fulfill({
        status: variables === undefined ? 400 : 200,
        body: JSON.stringify(variables),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (
      route
        .request()
        .url()
        .match(/\/v2\/process-definitions\/\d+\/xml/)
    ) {
      return route.fulfill({
        status: xml === undefined ? 400 : 200,
        body: xml,
        headers: {
          'content-type': 'application/xml',
        },
      });
    }

    if (route.request().url().includes('xml')) {
      return route.fulfill({
        status: xml === undefined ? 400 : 200,
        body: JSON.stringify(xml),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('incidents')) {
      return route.fulfill({
        status: incidents === undefined ? 400 : 200,
        body: JSON.stringify(incidents),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('flow-node-metadata')) {
      return route.fulfill({
        status: metaData === undefined ? 400 : 200,
        body: JSON.stringify(metaData),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/process-instances/')) {
      return route.fulfill({
        status: processInstanceDetail === undefined ? 400 : 200,
        body: JSON.stringify(processInstanceDetail),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/modify')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          id: '5f663d9d-1b0e-4243-90d9-43370f4b707c',
          name: null,
          type: 'MODIFY_PROCESS_INSTANCE',
          startDate: '2023-10-04T11:35:28.241+0200',
          endDate: null,
          username: 'demo',
          instancesCount: 1,
          operationsTotalCount: 1,
          operationsFinishedCount: 0,
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/operation')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          id: '4dccc4e0-7658-49d9-9361-cf9e73ee2052',
          name: null,
          type: 'DELETE_PROCESS_INSTANCE',
          startDate: '2023-10-04T14:25:23.613+0200',
          endDate: null,
          username: 'demo',
          instancesCount: 1,
          operationsTotalCount: 1,
          operationsFinishedCount: 0,
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

export type {InstanceMock};
export {mockResponses};
export {completedInstance} from './completedInstance.mocks';
export {completedOrderProcessInstance} from './completedOrderProcessInstance.mocks';
export {eventBasedGatewayProcessInstance} from './eventBasedGatewayProcessInstance.mocks';
export {instanceWithIncident} from './instanceWithIncident.mocks';
export {orderProcessInstance} from './orderProcessInstance.mocks';
export {runningInstance} from './runningInstance.mocks';
export {runningOrderProcessInstance} from './runningOrderProcessInstance.mocks';
export {compensationProcessInstance} from './compensationProcessInstance.mocks';
