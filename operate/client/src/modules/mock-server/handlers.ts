/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type RequestHandler, http, HttpResponse, passthrough} from 'msw';
import {
  MOCK_AGENT_INSTANCE_KEY,
  MOCK_AGENT_DEFINITION_KEY,
  MOCK_AGENT_DEFINITION_ID,
  AGENT_BPMN_XML,
  MOCK_AGENT_PROCESS_INSTANCE,
  MOCK_AGENT_PROCESS_DEFINITION,
  MOCK_AGENT_ELEMENT_INSTANCES,
  MOCK_AGENT_ELEMENT_STATISTICS,
  MOCK_AGENT_SEQUENCE_FLOWS,
  MOCK_AGENT_VARIABLES,
  MOCK_AGENT_JOBS,
} from './agentDemoData';

const PAGE_DEFAULTS = {
  totalItems: 0,
  startCursor: null,
  endCursor: null,
  hasMoreTotalItems: false,
};

const handlers: RequestHandler[] = [
  // GET single process instance
  http.get('*/v2/process-instances/:processInstanceKey', ({params}) => {
    if (params.processInstanceKey === MOCK_AGENT_INSTANCE_KEY) {
      return HttpResponse.json(MOCK_AGENT_PROCESS_INSTANCE);
    }
    return passthrough();
  }),

  // POST process instances search — inject mock instance at the top
  http.post('*/v2/process-instances/search', async ({request}) => {
    const body = (await request.json()) as Record<string, unknown>;
    const filter = body?.filter as Record<string, unknown> | undefined;

    const parentFilter = filter?.parentProcessInstanceKey;
    if (parentFilter) {
      return passthrough();
    }

    const processDefIdFilter =
      (filter?.processDefinitionId as Record<string, unknown>)?.$eq ??
      filter?.processDefinitionId;

    if (
      processDefIdFilter &&
      processDefIdFilter !== MOCK_AGENT_DEFINITION_ID
    ) {
      return passthrough();
    }

    const shouldIncludeMock =
      !processDefIdFilter || processDefIdFilter === MOCK_AGENT_DEFINITION_ID;

    if (!shouldIncludeMock) {
      return passthrough();
    }

    try {
      const realResponse = await fetch(request.clone());
      if (realResponse.ok) {
        const realData = (await realResponse.json()) as {
          items: unknown[];
          page: typeof PAGE_DEFAULTS;
        };
        return HttpResponse.json({
          items: [MOCK_AGENT_PROCESS_INSTANCE, ...realData.items],
          page: {
            ...realData.page,
            totalItems: (realData.page.totalItems ?? 0) + 1,
          },
        });
      }
    } catch {
      // Backend unavailable — return mock-only
    }

    return HttpResponse.json({
      items: [MOCK_AGENT_PROCESS_INSTANCE],
      page: {...PAGE_DEFAULTS, totalItems: 1},
    });
  }),

  // POST process definitions search — inject mock definition
  http.post('*/v2/process-definitions/search', async ({request}) => {
    try {
      const realResponse = await fetch(request.clone());
      if (realResponse.ok) {
        const realData = (await realResponse.json()) as {
          items: unknown[];
          page: typeof PAGE_DEFAULTS;
        };
        const hasMock = (realData.items as Array<{processDefinitionKey?: string}>).some(
          (d) => d.processDefinitionKey === MOCK_AGENT_DEFINITION_KEY,
        );
        if (hasMock) {
          return HttpResponse.json(realData);
        }
        return HttpResponse.json({
          items: [MOCK_AGENT_PROCESS_DEFINITION, ...realData.items],
          page: {
            ...realData.page,
            totalItems: (realData.page.totalItems ?? 0) + 1,
          },
        });
      }
    } catch {
      // Backend unavailable
    }

    return HttpResponse.json({
      items: [MOCK_AGENT_PROCESS_DEFINITION],
      page: {...PAGE_DEFAULTS, totalItems: 1},
    });
  }),

  // GET process definition XML
  http.get(
    '*/v2/process-definitions/:processDefinitionKey/xml',
    ({params}) => {
      if (params.processDefinitionKey === MOCK_AGENT_DEFINITION_KEY) {
        return HttpResponse.text(AGENT_BPMN_XML);
      }
      return passthrough();
    },
  ),

  // GET element instance statistics for process instance
  http.get(
    '*/v2/process-instances/:processInstanceKey/statistics/element-instances',
    ({params}) => {
      if (params.processInstanceKey === MOCK_AGENT_INSTANCE_KEY) {
        return HttpResponse.json(MOCK_AGENT_ELEMENT_STATISTICS);
      }
      return passthrough();
    },
  ),

  // POST element instances search
  http.post('*/v2/element-instances/search', async ({request}) => {
    const body = (await request.json()) as Record<string, unknown>;
    const filter = body?.filter as Record<string, unknown> | undefined;

    const piKeyFilter =
      (filter?.processInstanceKey as Record<string, unknown>)?.$eq ??
      filter?.processInstanceKey;

    // Also check if the scope key matches a mock element (tree expansion requests
    // only send elementInstanceScopeKey, not processInstanceKey)
    const scopeFilter = filter?.elementInstanceScopeKey as string | undefined;
    const isMockScope =
      scopeFilter !== undefined &&
      MOCK_AGENT_ELEMENT_INSTANCES.some(
        (el) => el.elementInstanceKey === scopeFilter,
      );

    if (piKeyFilter !== MOCK_AGENT_INSTANCE_KEY && !isMockScope) {
      return passthrough();
    }

    let items = [...MOCK_AGENT_ELEMENT_INSTANCES];

    if (scopeFilter) {
      items = items.filter(
        (el) =>
          el.flowScopeKey === scopeFilter &&
          el.elementInstanceKey !== scopeFilter,
      );
    }

    const elementIdFilter = filter?.elementId;
    if (elementIdFilter) {
      items = items.filter((el) => el.elementId === elementIdFilter);
    }

    const typeFilter = filter?.type;
    if (typeFilter) {
      items = items.filter((el) => el.type === typeFilter);
    }

    const sort = body?.sort as Array<{field: string; order: string}> | undefined;
    if (sort?.length) {
      const {field, order} = sort[0]!;
      items.sort((a, b) => {
        const aVal = String((a as Record<string, unknown>)[field] ?? '');
        const bVal = String((b as Record<string, unknown>)[field] ?? '');
        return order?.toUpperCase() === 'ASC'
          ? aVal.localeCompare(bVal)
          : bVal.localeCompare(aVal);
      });
    }

    const page = body?.page as {limit?: number; searchAfter?: unknown[]} | undefined;
    const limit = page?.limit ?? 50;

    return HttpResponse.json({
      items: items.slice(0, limit),
      page: {
        ...PAGE_DEFAULTS,
        totalItems: items.length,
      },
    });
  }),

  // GET single element instance
  http.get('*/v2/element-instances/:elementInstanceKey', ({params}) => {
    const found = MOCK_AGENT_ELEMENT_INSTANCES.find(
      (el) => el.elementInstanceKey === params.elementInstanceKey,
    );
    if (found) {
      return HttpResponse.json(found);
    }
    return passthrough();
  }),

  // GET sequence flows
  http.get(
    '*/v2/process-instances/:processInstanceKey/sequence-flows',
    ({params}) => {
      if (params.processInstanceKey === MOCK_AGENT_INSTANCE_KEY) {
        return HttpResponse.json(MOCK_AGENT_SEQUENCE_FLOWS);
      }
      return passthrough();
    },
  ),

  // POST variables search
  http.post('*/v2/variables/search', async ({request}) => {
    const body = (await request.json()) as Record<string, unknown>;
    const filter = body?.filter as Record<string, unknown> | undefined;

    const piKeyFilter =
      (filter?.processInstanceKey as Record<string, unknown>)?.$eq ??
      filter?.processInstanceKey;

    if (piKeyFilter !== MOCK_AGENT_INSTANCE_KEY) {
      return passthrough();
    }

    let items = [...MOCK_AGENT_VARIABLES];

    const scopeFilter = filter?.scopeKey;
    if (scopeFilter) {
      const scopeVal =
        (scopeFilter as Record<string, unknown>)?.$eq ?? scopeFilter;
      items = items.filter((v) => v.scopeKey === scopeVal);
    }

    return HttpResponse.json({
      items,
      page: {...PAGE_DEFAULTS, totalItems: items.length},
    });
  }),

  // POST incidents search for process instance
  http.post(
    '*/v2/process-instances/:processInstanceKey/incidents/search',
    ({params}) => {
      if (params.processInstanceKey === MOCK_AGENT_INSTANCE_KEY) {
        return HttpResponse.json({
          items: [],
          page: {...PAGE_DEFAULTS, totalItems: 0},
        });
      }
      return passthrough();
    },
  ),

  // POST incidents search for element instance
  http.post(
    '*/v2/element-instances/:elementInstanceKey/incidents/search',
    ({params}) => {
      const isMockElement = MOCK_AGENT_ELEMENT_INSTANCES.some(
        (el) => el.elementInstanceKey === params.elementInstanceKey,
      );
      if (isMockElement) {
        return HttpResponse.json({
          items: [],
          page: {...PAGE_DEFAULTS, totalItems: 0},
        });
      }
      return passthrough();
    },
  ),

  // GET call hierarchy
  http.get(
    '*/v2/process-instances/:processInstanceKey/call-hierarchy',
    ({params}) => {
      if (params.processInstanceKey === MOCK_AGENT_INSTANCE_KEY) {
        return HttpResponse.json([]);
      }
      return passthrough();
    },
  ),

  // POST jobs search
  http.post('*/v2/jobs/search', async ({request}) => {
    const body = (await request.json()) as Record<string, unknown>;
    const filter = body?.filter as Record<string, unknown> | undefined;

    const piKeyFilter = filter?.processInstanceKey;
    const elementInstanceFilter = filter?.elementInstanceKey;

    const isMockElement =
      elementInstanceFilter &&
      MOCK_AGENT_ELEMENT_INSTANCES.some(
        (el) => el.elementInstanceKey === elementInstanceFilter,
      );

    if (piKeyFilter === MOCK_AGENT_INSTANCE_KEY || isMockElement) {
      let items = [...MOCK_AGENT_JOBS];

      if (elementInstanceFilter) {
        items = items.filter(
          (j) => j.elementInstanceKey === elementInstanceFilter,
        );
      }

      return HttpResponse.json({
        items,
        page: {...PAGE_DEFAULTS, totalItems: items.length},
      });
    }

    return passthrough();
  }),

  // POST audit logs search
  http.post('*/v2/audit-logs/search', async ({request}) => {
    const body = (await request.json()) as Record<string, unknown>;
    const filter = body?.filter as Record<string, unknown> | undefined;

    if (filter?.processInstanceKey === MOCK_AGENT_INSTANCE_KEY) {
      return HttpResponse.json({
        items: [],
        page: {...PAGE_DEFAULTS, totalItems: 0},
      });
    }

    return passthrough();
  }),

  // POST definition-level statistics (for the process list diagram overlays)
  http.post(
    '*/v2/process-definitions/:processDefinitionKey/statistics/element-instances',
    ({params}) => {
      if (params.processDefinitionKey === MOCK_AGENT_DEFINITION_KEY) {
        return HttpResponse.json(MOCK_AGENT_ELEMENT_STATISTICS);
      }
      return passthrough();
    },
  ),

  // POST batch operations search (needed for toolbar)
  http.post('*/v2/batch-operations/search', () => {
    return passthrough();
  }),
];

export {handlers};
