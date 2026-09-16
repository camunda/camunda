import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockGetIncidentProcessInstanceStatisticsByErrorEndpoint,
	mockGetProcessDefinitionInstanceStatisticsEndpoint,
	mockLicenseEndpoint,
	mockSystemConfigurationEndpoint,
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
	mockQueryBatchOperationItemsEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {createProcessDefinitionInstanceStatistics} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createProcessInstance,
	createQueryProcessInstancesResponse,
} from '#/shared-test-modules/api-mocks/process-instances';
import {createQueryBatchOperationItemsResponse} from '#/shared-test-modules/api-mocks/batch-operations';

const STATS_WITH_INSTANCES = createPaginatedResponse({
	items: [
		createProcessDefinitionInstanceStatistics({
			processDefinitionId: 'process-1',
			latestProcessDefinitionName: 'Process One',
			activeInstancesWithoutIncidentCount: 10,
			activeInstancesWithIncidentCount: 3,
		}),
		createProcessDefinitionInstanceStatistics({
			processDefinitionId: 'process-2',
			latestProcessDefinitionName: 'Process Two',
			activeInstancesWithoutIncidentCount: 5,
			activeInstancesWithIncidentCount: 0,
		}),
	],
	page: {totalItems: 2, startCursor: null, endCursor: null, hasMoreTotalItems: false},
});

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		mockGetProcessDefinitionInstanceStatisticsEndpoint({successResponse: HttpResponse.json(STATS_WITH_INSTANCES)}),
		mockGetIncidentProcessInstanceStatisticsByErrorEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({items: [createProcessDefinition({name: 'Process One', processDefinitionId: 'process-1'})]}),
			),
		}),
		mockQueryBatchOperationItemsEndpoint({successResponse: HttpResponse.json(createQueryBatchOperationItemsResponse())}),
		mockQueryProcessInstancesEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessInstancesResponse({items: [createProcessInstance({processInstanceKey: '1001', processDefinitionName: 'Process One'})]}),
			),
		}),
		mockGetProcessInstanceWaitStateStatisticsEndpoint({successResponse: HttpResponse.json(createPaginatedResponse())}),
	);
});

async function logRowBorders(page: import('@playwright/test').Page, label: string) {
	const rows = await page.locator('[data-slot="table-row"]').evaluateAll((els) =>
		els.map((el) => {
			const style = getComputedStyle(el);
			return {borderBottomColor: style.borderBottomColor, ownBorderVar: style.getPropertyValue('--border')};
		}),
	);
	console.log(`[${label}] table-row borders:`, JSON.stringify(rows));
}

test('repro: row border color after navigating to processes and back', async ({page, operatePreviewPage}) => {
	await operatePreviewPage.goto();
	await expect(operatePreviewPage.processesByNameTile).toBeVisible();
	await logRowBorders(page, 'before');

	await page.getByText('Process One').first().click();
	await expect(page).toHaveURL(/\/operate\/processes/);
	await page.waitForLoadState('networkidle');

	await page.goBack();
	await expect(page).toHaveURL(/\/operate-preview/);
	await page.waitForLoadState('networkidle');
	await page.waitForTimeout(500);

	await logRowBorders(page, 'after');
	await page.screenshot({path: 'test-results/row-border-after.png', fullPage: true});
});
