/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Route} from '@playwright/test';
import type {
  Variable,
  GetProcessDefinitionStatisticsResponseBody,
  GetProcessInstanceCallHierarchyResponseBody,
  GetProcessInstanceSequenceFlowsResponseBody,
  ProcessInstance,
  QueryProcessInstanceIncidentsResponseBody,
  QueryElementInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {
  ProcessInstanceEntity,
  MetaDataDto,
  ProcessInstanceIncidentsDto,
  SequenceFlowsDto,
} from '@/types';

type InstanceMock = {
  xml: string;
  detail: ProcessInstanceEntity;
  detailV2: ProcessInstance;
  callHierarchy: GetProcessInstanceCallHierarchyResponseBody;
  elementInstances: QueryElementInstancesResponseBody;
  statistics: GetProcessDefinitionStatisticsResponseBody;
  sequenceFlows: SequenceFlowsDto;
  sequenceFlowsV2: GetProcessInstanceSequenceFlowsResponseBody;
  variables: Variable[];
  incidents?: ProcessInstanceIncidentsDto;
  incidentsV2?: QueryProcessInstanceIncidentsResponseBody;
  metaData?: MetaDataDto;
};

function mockResponses({
  processInstanceDetail,
  processInstanceDetailV2,
  callHierarchy,
  elementInstances,
  statistics,
  sequenceFlows,
  sequenceFlowsV2,
  variables,
  xml,
  incidents,
  incidentsV2,
  metaData,
}: {
  processInstanceDetail?: ProcessInstanceEntity;
  processInstanceDetailV2?: ProcessInstance;
  callHierarchy?: GetProcessInstanceCallHierarchyResponseBody;
  elementInstances?: QueryElementInstancesResponseBody;
  statistics?: GetProcessDefinitionStatisticsResponseBody;
  sequenceFlows?: SequenceFlowsDto;
  sequenceFlowsV2?: GetProcessInstanceSequenceFlowsResponseBody;
  variables?: Variable[];
  xml?: string;
  incidents?: ProcessInstanceIncidentsDto;
  incidentsV2?: QueryProcessInstanceIncidentsResponseBody;
  metaData?: MetaDataDto;
}) {
  return (route: Route) => {
    if (route.request().url().includes('/v2/authentication/me')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
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

    if (route.request().url().includes('/v2/element-instances/search')) {
      let filteredInstancesResponse = elementInstances;
      const elementId: string | undefined = route.request().postDataJSON()
        ?.filter?.elementId;

      if (elementId && elementInstances) {
        const filteredItems = elementInstances.items.filter(
          (instance) => instance.elementId === elementId,
        );
        filteredInstancesResponse = {
          items: filteredItems,
          page: {totalItems: filteredItems.length},
        };
      }
      return route.fulfill({
        status: filteredInstancesResponse === undefined ? 400 : 200,
        body: JSON.stringify(filteredInstancesResponse),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/v2/batch-operation-items/search')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({items: [], page: {totalItems: 0}}),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('statistics/element-instances')) {
      return route.fulfill({
        status: statistics === undefined ? 400 : 200,
        body: JSON.stringify(statistics),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('sequence-flows')) {
      if (route.request().url().includes('v2')) {
        return route.fulfill({
          status: sequenceFlowsV2 === undefined ? 400 : 200,
          body: JSON.stringify(sequenceFlowsV2),
          headers: {
            'content-type': 'application/json',
          },
        });
      }
      return route.fulfill({
        status: sequenceFlows === undefined ? 400 : 200,
        body: JSON.stringify(sequenceFlows),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('v2/variables/search')) {
      return route.fulfill({
        status: variables === undefined ? 400 : 200,
        body: JSON.stringify({
          items: variables,
          page: {
            totalItems: variables?.length ?? 0,
          },
        }),
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

    if (
      route
        .request()
        .url()
        .match(/\/v2\/process-instances\/\d+\/incidents\/search/)
    ) {
      return route.fulfill({
        status: incidentsV2 === undefined ? 400 : 200,
        body: JSON.stringify(incidentsV2),
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

    if (route.request().url().includes('call-hierarchy')) {
      return route.fulfill({
        status: callHierarchy === undefined ? 400 : 200,
        body: JSON.stringify(callHierarchy),
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

    if (route.request().url().includes('/v2/process-instances/')) {
      return route.fulfill({
        status: processInstanceDetailV2 === undefined ? 400 : 200,
        body: JSON.stringify(processInstanceDetailV2),
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
