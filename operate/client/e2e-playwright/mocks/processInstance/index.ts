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
  QueryAuditLogsResponseBody,
  QueryElementInstanceInspectionResponseBody,
  GetProcessInstanceWaitStateStatisticsResponseBody,
  QueryAgentInstancesResponseBody,
  QueryAgentInstanceHistoryResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';

type InstanceMock = {
  xml: string;
  detail: ProcessInstance;
  callHierarchy: GetProcessInstanceCallHierarchyResponseBody;
  elementInstances: QueryElementInstancesResponseBody;
  statistics: GetProcessDefinitionStatisticsResponseBody;
  sequenceFlows: GetProcessInstanceSequenceFlowsResponseBody;
  variables: Variable[];
  incidents?: QueryProcessInstanceIncidentsResponseBody;
  waitStates?: QueryElementInstanceInspectionResponseBody;
  waitStateStatistics?: GetProcessInstanceWaitStateStatisticsResponseBody;
  agentInstances?: QueryAgentInstancesResponseBody;
  agentInstanceHistory?: QueryAgentInstanceHistoryResponseBody;
};

function mockResponses({
  processInstanceDetail,
  callHierarchy,
  elementInstances,
  statistics,
  sequenceFlows,
  variables,
  xml,
  incidents,
  auditLogs,
  waitStates,
  waitStateStatistics,
  agentInstances,
  agentInstanceHistory,
}: {
  processInstanceDetail?: ProcessInstance;
  callHierarchy?: GetProcessInstanceCallHierarchyResponseBody;
  elementInstances?: QueryElementInstancesResponseBody;
  statistics?: GetProcessDefinitionStatisticsResponseBody;
  sequenceFlows?: GetProcessInstanceSequenceFlowsResponseBody;
  variables?: Variable[];
  xml?: string;
  incidents?: QueryProcessInstanceIncidentsResponseBody;
  auditLogs?: QueryAuditLogsResponseBody;
  waitStates?: QueryElementInstanceInspectionResponseBody;
  waitStateStatistics?: GetProcessInstanceWaitStateStatisticsResponseBody;
  agentInstances?: QueryAgentInstancesResponseBody;
  agentInstanceHistory?: QueryAgentInstanceHistoryResponseBody;
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

    if (
      route.request().url().includes('/v2/element-instances/wait-states/search')
    ) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(
          waitStates ?? {
            items: [],
            page: {
              totalItems: 0,
              startCursor: null,
              endCursor: null,
              hasMoreTotalItems: false,
            },
          },
        ),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (
      route
        .request()
        .url()
        .match(/\/v2\/agent-instances\/\d+\/history\/search/)
    ) {
      const requestBody = route.request().postDataJSON() ?? {};
      const roleFilter: string | undefined = requestBody?.filter?.role;
      const elementInstanceKeyFilter: string | undefined =
        requestBody?.filter?.elementInstanceKey;
      const limit: number | undefined = requestBody?.page?.limit;

      let items = agentInstanceHistory?.items ?? [];
      if (roleFilter) {
        items = items.filter((item) => item.role === roleFilter);
      }
      if (elementInstanceKeyFilter) {
        items = items.filter(
          (item) => item.elementInstanceKey === elementInstanceKeyFilter,
        );
      }
      const totalItems = items.length;
      if (typeof limit === 'number') {
        items = items.slice(0, limit);
      }

      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          items,
          page: {
            totalItems,
            startCursor: null,
            endCursor: null,
            hasMoreTotalItems: false,
          },
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/v2/agent-instances/search')) {
      const elementId: string | undefined = route.request().postDataJSON()
        ?.filter?.elementId;

      let filteredAgentInstances = agentInstances;
      if (elementId && agentInstances) {
        const filteredItems = agentInstances.items.filter(
          (instance) => instance.elementId === elementId,
        );
        filteredAgentInstances = {
          items: filteredItems,
          page: {
            totalItems: filteredItems.length,
            startCursor: null,
            endCursor: null,
            hasMoreTotalItems: false,
          },
        };
      }

      return route.fulfill({
        status: 200,
        body: JSON.stringify(
          filteredAgentInstances ?? {
            items: [],
            page: {
              totalItems: 0,
              startCursor: null,
              endCursor: null,
              hasMoreTotalItems: false,
            },
          },
        ),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/v2/jobs/search')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          items: [],
          page: {
            totalItems: 0,
            startCursor: null,
            endCursor: null,
            hasMoreTotalItems: false,
          },
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    const elementInstanceByKeyMatch = route
      .request()
      .url()
      .match(/\/v2\/element-instances\/(\d+)(?:\?|$)/);
    if (route.request().method() === 'GET' && elementInstanceByKeyMatch) {
      const elementInstanceKey = elementInstanceByKeyMatch[1];
      const item = elementInstances?.items.find(
        (instance) => instance.elementInstanceKey === elementInstanceKey,
      );
      return route.fulfill({
        status: item === undefined ? 404 : 200,
        body: JSON.stringify(item ?? {}),
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
          page: {
            totalItems: filteredItems.length,
            startCursor: null,
            endCursor: null,
            hasMoreTotalItems: false,
          },
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

    if (route.request().url().includes('statistics/wait-states')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify(waitStateStatistics ?? {items: []}),
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
        status: incidents === undefined ? 400 : 200,
        body: JSON.stringify(incidents),
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

    if (route.request().url().includes('/v2/process-instances/')) {
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

    if (route.request().url().includes('/v2/audit-logs/search')) {
      return route.fulfill({
        status: auditLogs === undefined ? 400 : 200,
        body: JSON.stringify(auditLogs),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    return route.continue();
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
export {waitStateProcessInstance} from './waitStateProcessInstance.mocks';
export {waitStateRunningInstance} from './waitStateRunningInstance.mocks';
export {runningOrderProcessInstance} from './runningOrderProcessInstance.mocks';
export {compensationProcessInstance} from './compensationProcessInstance.mocks';
export {documentReferenceProcessInstance} from './documentReferenceProcessInstance.mocks';
export {
  agentProcessWithOneActiveInstance,
  agentProcessWithTwoActiveInstances,
} from './aiAgentProcessInstance.mock';
