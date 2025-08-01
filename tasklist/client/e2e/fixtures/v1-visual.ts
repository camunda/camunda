/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test as base} from '@/fixtures/test';
import {type Page} from '@playwright/test';
import {bpmnXml} from '@/mocks/v1/bpmnXml';
import {nonFormTask, type Task} from '@/mocks/v1/task';
import {emptyVariables, type Variable} from '@/mocks/v1/variables';
import schema from '@/resources/bigForm.json' assert {type: 'json'};
import type {Process} from '@/mocks/v1/processes';

const MOCK_TENANTS = [
  {
    tenantId: 'tenantA',
    name: 'Tenant A',
  },
  {
    tenantId: 'tenantB',
    name: 'Tenant B',
  },
];

type PlaywrightFixtures = {
  page: Page;
  mockGetProcessesRequest: (processes?: Process[]) => void;
  mockGetProcessRequest: (process?: Process) => void;
  mockGetTasksRequest: (tasks?: Task[]) => void;
  mockGetTaskRequest: (task?: Task) => void;
  mockGetTaskVariablesRequest: (params: {
    variables?: Variable[];
    taskId: string;
  }) => void;
  mockGetFormRequest: (params: {
    formId: string;
    processDefinitionKey: string;
  }) => void;
  mockClientConfigRequest: (params: unknown) => void;
  mockHasConsentedToStartProcess: () => void;
};

const test = base.extend<PlaywrightFixtures>({
  page: async ({page}, use) => {
    await page.route('/v2/license', (route) =>
      route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          validLicense: false,
          licenseType: 'unknown',
        }),
      }),
    );
    await page.route('/v2/authentication/me', (route) =>
      route.fulfill({
        status: 200,
        body: JSON.stringify({
          username: 'demo',
          displayName: 'demo',
          salesPlanType: null,
          roles: null,
          c8Links: [],
          tenants: MOCK_TENANTS,
        }),
        headers: {
          'content-type': 'application/json',
        },
      }),
    );
    await page.route('/client-config.js', (route) =>
      route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'text/javascript;charset=UTF-8',
        },
        body: `window.clientConfig = {
                isEnterprise: false,
                isMultiTenancyEnabled: false,
                canLogout: true,
                isLoginDelegated: false,
                contextPath: "",
                baseName: "/",
                organizationId: null,
                clusterId: null,
                stage: null,
                mixpanelToken: null,
                mixpanelAPIHost: null,
                isResourcePermissionsEnabled: false,
                isUserAccessRestrictionsEnabled: true,
              };
            `,
      }),
    );

    await use(page);
  },
  mockGetProcessesRequest: async ({page}, use) => {
    await use(async (processes = []) => {
      await page.route('/v1/internal/processes*', (route) =>
        route.fulfill({
          status: 200,
          body: JSON.stringify(processes),
          headers: {
            'content-type': 'application/json',
          },
        }),
      );
    });
  },
  mockGetProcessRequest: async ({page}, use) => {
    await use(async (process) => {
      if (!process) return;
      await page.route(`/v1/internal/processes/${process.id}`, (route) =>
        route.fulfill({
          status: 200,
          body: JSON.stringify({
            ...process,
            sortValues: ['value'],
            bpmnXml: process.bpmnXml || bpmnXml,
          }),
          headers: {
            'content-type': 'application/json',
          },
        }),
      );
    });
  },
  mockGetTasksRequest: async ({page}, use) => {
    await use(async (tasks = []) => {
      await page.route('/v1/tasks/search', (route) =>
        route.fulfill({
          status: 200,
          body: JSON.stringify(tasks),
          headers: {
            'content-type': 'application/json',
          },
        }),
      );
    });
  },
  mockGetTaskRequest: async ({page}, use) => {
    await use(async (task = nonFormTask()) => {
      await page.route(`/v1/tasks/${task.id}`, (route) =>
        route.fulfill({
          status: 200,
          body: JSON.stringify(task),
          headers: {
            'content-type': 'application/json',
          },
        }),
      );
    });
  },
  mockGetTaskVariablesRequest: async ({page}, use) => {
    await use(async ({variables = emptyVariables, taskId}) => {
      await page.route(`/v1/tasks/${taskId}/variables/search`, (route) =>
        route.fulfill({
          status: 200,
          body: JSON.stringify(variables),
          headers: {
            'content-type': 'application/json',
          },
        }),
      );
    });
  },
  mockGetFormRequest: async ({page}, use) => {
    await use(async ({formId, processDefinitionKey}) => {
      await page.route(`/v1/forms/${formId}*`, (route) =>
        route.fulfill({
          status: 200,
          body: JSON.stringify({
            id: formId,
            processDefinitionKey,
            schema: JSON.stringify(schema),
          }),
          headers: {
            'content-type': 'application/json',
          },
        }),
      );
    });
  },
  mockClientConfigRequest: async ({page}, use) => {
    await use(async (params) => {
      await page.route('/client-config.js', (route) =>
        route.fulfill({
          status: 200,
          body: `window.clientConfig = ${JSON.stringify(params)};`,
          headers: {
            'Content-Type': 'text/javascript;charset=UTF-8',
          },
        }),
      );
    });
  },
  mockHasConsentedToStartProcess: async ({page}, use) => {
    await use(async () => {
      await page.addInitScript(`(() => {
        window.localStorage.setItem('hasConsentedToStartProcess', 'true');
      })()`);
    });
  },
});

export {test, MOCK_TENANTS};
