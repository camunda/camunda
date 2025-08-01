/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test as base} from '@/fixtures/test';
import {mockForm} from '@/mocks/v2/form';
import {type Page} from '@playwright/test';
import {
  type Form,
  type UserTask,
  type Variable,
  endpoints,
  type ProcessDefinition,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {unassignedTask} from '@/mocks/v2/task';
import {bpmnXml} from '@/mocks/v2/bpmnXml';

function getCollectionResponse<Item>(items: Item[]) {
  return {
    items,
    page: {
      totalItems: items.length,
      startCursor: '<startCursor>',
      endCursor: '<endCursor>',
    },
  };
}

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
  mockGetUserTaskFormRequest: (params: {
    form?: Partial<Form>;
    userTaskKey: string;
  }) => void;
  mockQueryUserTasksRequest: (userTasks?: UserTask[]) => void;
  mockGetUserTaskRequest: (userTask?: UserTask) => void;
  mockQueryVariablesByUserTaskRequest: (params: {
    variables?: Variable[];
    userTaskKey: string;
  }) => void;
  mockGetProcessDefinitionXmlRequest: (params: {
    processDefinitionKey: string;
    xml?: string;
  }) => void;
  mockClientConfigRequest: (params: unknown) => void;
  mockQueryProcessDefinitionsRequest: (
    processDefinitions?: ProcessDefinition[],
  ) => void;
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
                clientMode: 'v2',
              };
            `,
      }),
    );

    await use(page);
  },
  mockGetUserTaskFormRequest: async ({page}, use) => {
    await use(async ({form = {}, userTaskKey}) => {
      await page.route(
        endpoints.getUserTaskForm.getUrl({
          userTaskKey,
        }),
        (route) =>
          route.fulfill({
            status: 200,
            body: JSON.stringify(mockForm(form)),

            headers: {
              'content-type': 'application/json',
            },
          }),
      );
    });
  },
  mockQueryUserTasksRequest: async ({page}, use) => {
    await use(async (userTasks = []) => {
      await page.route(endpoints.queryUserTasks.getUrl(), (route) =>
        route.fulfill({
          status: 200,
          body: JSON.stringify(getCollectionResponse(userTasks)),
          headers: {
            'content-type': 'application/json',
          },
        }),
      );
    });
  },
  mockGetUserTaskRequest: async ({page}, use) => {
    await use(async (userTask = unassignedTask()) => {
      await page.route(
        endpoints.getUserTask.getUrl({
          userTaskKey: userTask.userTaskKey,
        }),
        (route) =>
          route.fulfill({
            status: 200,
            body: JSON.stringify(userTask),
            headers: {
              'content-type': 'application/json',
            },
          }),
      );
    });
  },
  mockQueryVariablesByUserTaskRequest: async ({page}, use) => {
    await use(async ({variables = [], userTaskKey}) => {
      await page.route(
        endpoints.queryVariablesByUserTask.getUrl({userTaskKey}),
        (route) =>
          route.fulfill({
            status: 200,
            body: JSON.stringify(getCollectionResponse(variables)),
            headers: {
              'content-type': 'application/json',
            },
          }),
      );
    });
  },
  mockGetProcessDefinitionXmlRequest: async ({page}, use) => {
    await use(async ({processDefinitionKey, xml = bpmnXml}) => {
      await page.route(
        endpoints.getProcessDefinitionXml.getUrl({
          processDefinitionKey,
        }),
        (route) =>
          route.fulfill({
            status: 200,
            body: xml,
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
  mockQueryProcessDefinitionsRequest: async ({page}, use) => {
    await use(async (processDefinitions = []) => {
      await page.route(endpoints.queryProcessDefinitions.getUrl(), (route) =>
        route.fulfill({
          status: 200,
          body: JSON.stringify(getCollectionResponse(processDefinitions)),
          headers: {
            'content-type': 'application/json',
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
