/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect, Route} from '@playwright/test';
import {test} from '../test-fixtures';
import {
  completedInstance,
  instanceWithIncident,
  runningInstance,
} from './processInstance.mocks';
import {
  FlowNodeInstanceDto,
  FlowNodeInstancesDto,
} from 'modules/api/fetchFlowNodeInstances';
import {ProcessInstanceDetailStatisticsDto} from 'modules/api/processInstances/fetchProcessInstanceDetailStatistics';
import {SequenceFlowsDto} from 'modules/api/processInstances/sequenceFlows';
import {ProcessInstanceIncidentsDto} from 'modules/api/processInstances/fetchProcessInstanceIncidents';
import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';

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
    if (route.request().url().includes('/api/authentications/user')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          userId: 'demo',
          displayName: 'demo',
          canLogout: true,
          permissions: ['read', 'write'],
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

    route.continue();
  };
}

test.describe('process instance page', () => {
  for (const theme of ['light', 'dark']) {
    test(`error page - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          xml: runningInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`running instance - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          flowNodeInstances: runningInstance.flowNodeInstances,
          statistics: runningInstance.statistics,
          sequenceFlows: runningInstance.sequenceFlows,
          variables: runningInstance.variables,
          xml: runningInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`add variable state - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          flowNodeInstances: runningInstance.flowNodeInstances,
          statistics: runningInstance.statistics,
          sequenceFlows: runningInstance.sequenceFlows,
          variables: runningInstance.variables,
          xml: runningInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      await processInstancePage.addVariableButton.click();
      await expect(page).toHaveScreenshot();
    });

    test(`edit variable state - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: runningInstance.detail,
          flowNodeInstances: runningInstance.flowNodeInstances,
          statistics: runningInstance.statistics,
          sequenceFlows: runningInstance.sequenceFlows,
          variables: runningInstance.variables,
          xml: runningInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page
        .getByRole('button', {
          name: /edit variable/i,
        })
        .click();

      await expect(page).toHaveScreenshot();
    });

    test(`instance with incident - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: instanceWithIncident.detail,
          flowNodeInstances: instanceWithIncident.flowNodeInstances,
          statistics: instanceWithIncident.statistics,
          sequenceFlows: instanceWithIncident.sequenceFlows,
          variables: instanceWithIncident.variables,
          xml: instanceWithIncident.xml,
          incidents: instanceWithIncident.incidents,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page
        .getByRole('button', {
          name: /view 1 incident in instance/i,
        })
        .click();

      await expect(page).toHaveScreenshot();
    });

    test(`completed instance - ${theme}`, async ({
      page,
      commonPage,
      processInstancePage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          processInstanceDetail: completedInstance.detail,
          flowNodeInstances: completedInstance.flowNodeInstances,
          statistics: completedInstance.statistics,
          sequenceFlows: completedInstance.sequenceFlows,
          variables: completedInstance.variables,
          xml: completedInstance.xml,
        }),
      );

      await processInstancePage.navigateToProcessInstance({
        id: '1',
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page.getByText(/show end date/i).click();

      await expect(page).toHaveScreenshot();
    });
  }
});
