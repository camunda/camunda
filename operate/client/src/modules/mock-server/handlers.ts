/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/*
 * Mock handlers for the AI agent visibility prototype.
 *
 * The mock scenarios are NOT injected into the processes list or the
 * process-definitions list — they only intercept requests that are already
 * scoped to one of the mock keys (typically when the URL is
 * `/processes/<mock-instance-key>` or when the frontend fetches data for a
 * mock element instance). The regular processes list shows real backend data.
 *
 * To reach the mock demo, navigate directly to the URLs printed by the
 * `browser.ts` setup on dev-server startup.
 */

import {type RequestHandler, http, HttpResponse, passthrough} from 'msw';
import {
  SCENARIOS,
  getScenarioByInstanceKey,
  getScenarioByDefinitionKey,
} from './scenarioRegistry';

const PAGE_DEFAULTS = {
  totalItems: 0,
  startCursor: null,
  endCursor: null,
  hasMoreTotalItems: false,
};

const allMockElementInstances = () =>
  SCENARIOS.flatMap((s) => s.elementInstances);

const findScenarioByElementInstanceKey = (key: string | undefined) =>
  key
    ? SCENARIOS.find((s) =>
        s.elementInstances.some((el) => el.elementInstanceKey === key),
      )
    : undefined;

const handlers: RequestHandler[] = [
  // GET single process instance
  http.get('*/v2/process-instances/:processInstanceKey', ({params}) => {
    const scenario = getScenarioByInstanceKey(
      params.processInstanceKey as string,
    );
    if (scenario) {
      return HttpResponse.json(scenario.processInstance);
    }
    return passthrough();
  }),

  // GET process definition XML
  http.get('*/v2/process-definitions/:processDefinitionKey/xml', ({params}) => {
    const scenario = getScenarioByDefinitionKey(
      params.processDefinitionKey as string,
    );
    if (scenario) {
      return HttpResponse.text(scenario.bpmnXml);
    }
    return passthrough();
  }),

  // GET element instance statistics for process instance
  http.get(
    '*/v2/process-instances/:processInstanceKey/statistics/element-instances',
    ({params}) => {
      const scenario = getScenarioByInstanceKey(
        params.processInstanceKey as string,
      );
      if (scenario) {
        return HttpResponse.json(scenario.elementStatistics);
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

    const scopeFilter = filter?.elementInstanceScopeKey as string | undefined;

    let scenario = piKeyFilter
      ? getScenarioByInstanceKey(piKeyFilter as string)
      : undefined;

    if (!scenario && scopeFilter) {
      scenario = findScenarioByElementInstanceKey(scopeFilter);
    }

    if (!scenario) {
      return passthrough();
    }

    let items = [...scenario.elementInstances];

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

    const sort = body?.sort as
      | Array<{field: string; order: string}>
      | undefined;
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

    const page = body?.page as
      | {limit?: number; searchAfter?: unknown[]}
      | undefined;
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
    const found = allMockElementInstances().find(
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
      const scenario = getScenarioByInstanceKey(
        params.processInstanceKey as string,
      );
      if (scenario) {
        return HttpResponse.json(scenario.sequenceFlows);
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

    const scenario = piKeyFilter
      ? getScenarioByInstanceKey(piKeyFilter as string)
      : undefined;

    if (!scenario) {
      return passthrough();
    }

    let items = [...scenario.variables];

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
      if (getScenarioByInstanceKey(params.processInstanceKey as string)) {
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
      const isMockElement = allMockElementInstances().some(
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
      if (getScenarioByInstanceKey(params.processInstanceKey as string)) {
        return HttpResponse.json([]);
      }
      return passthrough();
    },
  ),

  // POST jobs search
  http.post('*/v2/jobs/search', async ({request}) => {
    const body = (await request.json()) as Record<string, unknown>;
    const filter = body?.filter as Record<string, unknown> | undefined;

    const piKeyFilter = filter?.processInstanceKey as string | undefined;
    const elementInstanceFilter = filter?.elementInstanceKey as
      | string
      | undefined;

    let scenario = piKeyFilter
      ? getScenarioByInstanceKey(piKeyFilter)
      : undefined;

    if (!scenario && elementInstanceFilter) {
      scenario = findScenarioByElementInstanceKey(elementInstanceFilter);
    }

    if (scenario) {
      let items = [...scenario.jobs];

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

    const piKeyFilter = filter?.processInstanceKey as string | undefined;
    if (piKeyFilter && getScenarioByInstanceKey(piKeyFilter)) {
      return HttpResponse.json({
        items: [],
        page: {...PAGE_DEFAULTS, totalItems: 0},
      });
    }

    return passthrough();
  }),

  // POST decision instances search
  http.post('*/v2/decision-instances/search', async ({request}) => {
    const body = (await request.json()) as Record<string, unknown>;
    const filter = body?.filter as Record<string, unknown> | undefined;

    const elementInstanceFilter = filter?.elementInstanceKey as
      | string
      | undefined;

    if (
      elementInstanceFilter &&
      findScenarioByElementInstanceKey(elementInstanceFilter)
    ) {
      return HttpResponse.json({
        items: [],
        page: {...PAGE_DEFAULTS, totalItems: 0},
      });
    }

    return passthrough();
  }),

  // POST listeners search
  http.post(
    '*/v2/element-instances/:elementInstanceKey/listeners/search',
    ({params}) => {
      if (
        findScenarioByElementInstanceKey(params.elementInstanceKey as string)
      ) {
        return HttpResponse.json({
          items: [],
          page: {...PAGE_DEFAULTS, totalItems: 0},
        });
      }
      return passthrough();
    },
  ),
];

export {handlers};
