/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {cleanup} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {Toaster, toast} from '@camunda/design-system';
import {afterEach, describe, expect, vi} from 'vitest';
import {z} from 'zod';
import {it} from '#/vitest-modules/test-extend';
import {queryUsersRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockAssignUserToTenantEndpoint,
	mockDeleteTenantEndpoint,
	mockQueryTenantsEndpoint,
	mockQueryClientsByTenantEndpoint,
	mockQueryGroupsByTenantEndpoint,
	mockQueryMappingRulesByTenantEndpoint,
	mockQueryRolesByTenantEndpoint,
	mockQueryRolesEndpoint,
	mockQueryUsersByTenantEndpoint,
	mockQueryUsersEndpoint,
	mockUnassignUserFromTenantEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createQueryClientsByTenantResponse,
	createQueryGroupsByTenantResponse,
	createQueryUsersByTenantResponse,
	createTenant,
} from '#/shared-test-modules/api-mocks/tenants';
import type {TenantTab} from '#/admin/modules/tenants/detailSearchSchema';
import {AdminTenantDetailPage, type AdminTenantDetailPageProps} from './AdminTenantDetailPage';

async function renderPage(overrides: Partial<AdminTenantDetailPageProps> = {}) {
	const onSearchChange = vi.fn();
	const onDeleted = vi.fn();
	const tabs: TenantTab[] = ['users', 'groups', 'roles'];
	const screen = await renderWithRouter(
		() => (
			<>
				<AdminTenantDetailPage
					tenant={createTenant({tenantId: 'tenant-a', name: 'Tenant A', description: 'My tenant'})}
					tabs={tabs}
					isOidc={false}
					isCamundaGroupsEnabled={true}
					search={{}}
					onSearchChange={onSearchChange}
					onDeleted={onDeleted}
					{...overrides}
				/>
				<Toaster />
			</>
		),
		{path: '/admin/tenants'},
	);

	return {screen, onSearchChange, onDeleted};
}

describe('<AdminTenantDetailPage />', () => {
	afterEach(async () => {
		await cleanup();
		toast.dismiss();
	});

	it('should show the tenant details and the users tab by default', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane']))}),
		);

		const {screen} = await renderPage();

		await expect.element(screen.getByRole('heading', {name: 'Tenant A'})).toBeVisible();
		await expect.element(screen.getByText('My tenant')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'jane'})).toBeVisible();
	});

	it('should only offer the tabs it is given', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
		);

		const {screen} = await renderPage({tabs: ['users', 'roles']});

		await expect.element(screen.getByRole('tab', {name: 'Users'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Roles'})).toBeVisible();
		await expect.element(screen.getByRole('tab', {name: 'Groups'})).not.toBeInTheDocument();
	});

	it('should reset paging and sorting when the reader switches tab', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
		);

		const {screen, onSearchChange} = await renderPage({search: {page: 3, sortField: 'name', sortOrder: 'DESC'}});

		await userEvent.click(screen.getByRole('tab', {name: 'Roles'}));

		expect(onSearchChange).toHaveBeenCalledWith({
			tab: 'roles',
			page: undefined,
			pageSize: undefined,
			sortField: undefined,
			sortOrder: undefined,
		});
	});

	it('should show the groups of the tenant on the groups tab', async ({worker}) => {
		worker.use(
			mockQueryGroupsByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryGroupsByTenantResponse(['sales'])),
			}),
		);

		const {screen} = await renderPage({search: {tab: 'groups'}});

		await expect.element(screen.getByRole('cell', {name: 'sales'})).toBeVisible();
	});

	it('should step back to the last page when the requested page no longer exists', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane'])),
			}),
		);

		// when
		const {onSearchChange} = await renderPage({search: {page: 3}});

		// then
		await expect.poll(() => onSearchChange).toHaveBeenCalledWith({page: undefined}, {replace: true});
	});

	it('should show the clients of the tenant on the clients tab', async ({worker}) => {
		worker.use(
			mockQueryClientsByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryClientsByTenantResponse(['connectors'])),
			}),
		);

		const {screen} = await renderPage({tabs: ['users', 'clients'], isOidc: true, search: {tab: 'clients'}});

		await expect.element(screen.getByRole('cell', {name: 'connectors'})).toBeVisible();
	});

	it('should sort the roles of the tenant by name when the reader sorts that column', async ({worker}) => {
		worker.use(
			mockQueryRolesByTenantEndpoint({
				successResponse: HttpResponse.json({items: [{roleId: 'ops', name: 'Operators'}], page: {totalItems: 1}}),
			}),
		);
		const {screen, onSearchChange} = await renderPage({search: {tab: 'roles'}});

		await userEvent.click(screen.getByRole('button', {name: 'Name'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'name', sortOrder: undefined, page: undefined});
	});

	it('should show the claim columns of the mapping rules of the tenant', async ({worker}) => {
		worker.use(
			mockQueryMappingRulesByTenantEndpoint({
				successResponse: HttpResponse.json({
					items: [{mappingRuleId: 'okta', name: 'Okta users', claimName: 'groups', claimValue: 'engineering'}],
					page: {totalItems: 1},
				}),
			}),
		);

		const {screen} = await renderPage({tabs: ['users', 'mapping-rules'], search: {tab: 'mapping-rules'}});

		await expect.element(screen.getByRole('button', {name: 'Claim name'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'engineering'})).toBeVisible();
	});

	it('should show an empty state when nothing is assigned', async ({worker}) => {
		worker.use(
			mockQueryRolesByTenantEndpoint({successResponse: HttpResponse.json({items: [], page: {totalItems: 0}})}),
			mockQueryRolesEndpoint({successResponse: HttpResponse.json({items: [], page: {totalItems: 0}})}),
		);

		const {screen} = await renderPage({search: {tab: 'roles'}});

		await expect.element(screen.getByText('No roles assigned to this tenant yet')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Learn more about roles'})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Assign role'}));

		await expect.element(screen.getByRole('dialog')).toBeVisible();
	});

	it('should assign a user picked from the user search', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json({
					items: [{username: 'jane', name: 'Jane', email: 'jane@example.com'}],
					page: {totalItems: 1},
				}),
			}),
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);

		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Users'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane'}));
		await userEvent.click(screen.getByRole('heading', {name: 'Assign user'}));
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse()),
				delay: 'infinite',
			}),
		);
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Assign user'}));

		await expect.element(screen.getByText('Assignment saved')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'jane'})).toBeVisible();
	});

	it('should show the members the server confirms after an assignment', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'jane'}], page: {totalItems: 1}}),
			}),
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Users'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane'}));
		await userEvent.click(screen.getByRole('heading', {name: 'Assign user'}));
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane', 'zoe'])),
			}),
		);
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Assign user'}));

		await expect.element(screen.getByRole('cell', {name: 'zoe'})).toBeVisible();
	});

	it('should show a single toast when several users are assigned at once', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'jane'}, {username: 'john'}], page: {totalItems: 2}}),
			}),
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Users'}));
		await userEvent.click(screen.getByRole('option', {name: 'jane'}));
		await userEvent.click(screen.getByRole('option', {name: 'john'}));
		await userEvent.click(screen.getByRole('heading', {name: 'Assign user'}));
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane', 'john', 'zoe'])),
			}),
		);
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Assign user'}));

		await expect.element(screen.getByText('2 members assigned')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'zoe'})).toBeVisible();
	});

	it('should keep the assigned members when another member of the batch fails', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json({
					items: [{username: 'alice'}, {username: 'bob'}, {username: 'dave'}],
					page: {totalItems: 3},
				}),
			}),
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Users'}));
		await userEvent.click(screen.getByRole('option', {name: 'alice'}));
		await userEvent.click(screen.getByRole('option', {name: 'bob'}));
		await userEvent.click(screen.getByRole('option', {name: 'dave'}));
		await userEvent.click(screen.getByRole('heading', {name: 'Assign user'}));
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['alice', 'bob', 'dave'])),
			}),
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 500}), once: true}),
		);
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Assign user'}));

		await expect.element(screen.getByText('2 of 3 assigned')).toBeVisible();
	});

	it('should count an already assigned member as assigned when another member fails', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'alice'}, {username: 'bob'}], page: {totalItems: 2}}),
			}),
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 500})}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Users'}));
		await userEvent.click(screen.getByRole('option', {name: 'alice'}));
		await userEvent.click(screen.getByRole('option', {name: 'bob'}));
		await userEvent.click(screen.getByRole('heading', {name: 'Assign user'}));
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['alice', 'bob'])),
			}),
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 409}), once: true}),
		);
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Assign user'}));

		await expect.element(screen.getByText('1 of 2 assigned')).toBeVisible();
	});

	it('should treat an already assigned member as assigned when retrying a batch', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json({items: [{username: 'alice'}, {username: 'bob'}], page: {totalItems: 2}}),
			}),
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Users'}));
		await userEvent.click(screen.getByRole('option', {name: 'alice'}));
		await userEvent.click(screen.getByRole('option', {name: 'bob'}));
		await userEvent.click(screen.getByRole('heading', {name: 'Assign user'}));
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['alice', 'bob'])),
			}),
			mockAssignUserToTenantEndpoint({successResponse: new HttpResponse(null, {status: 409}), once: true}),
		);
		await userEvent.click(screen.getByRole('dialog').getByRole('button', {name: 'Assign user'}));

		await expect.element(screen.getByText('2 members assigned')).toBeVisible();
	});

	it('should still offer users when the first candidates are all assigned on the current page', async ({worker}) => {
		// given
		const everyone = Array.from({length: 102}, (_, index) => `user-${String(index).padStart(3, '0')}`);
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse(everyone.slice(0, 100))),
			}),
			mockQueryUsersEndpoint({
				schema: queryUsersRequestBodySchema.and(
					z.object({
						filter: z.object({
							username: z.object({
								$notIn: z.tuple([
									z.literal('user-000'),
									z.literal('user-001'),
									z.literal('user-002'),
									z.literal('user-003'),
									z.literal('user-004'),
									z.literal('user-005'),
									z.literal('user-006'),
									z.literal('user-007'),
									z.literal('user-008'),
									z.literal('user-009'),
									z.literal('user-010'),
									z.literal('user-011'),
									z.literal('user-012'),
									z.literal('user-013'),
									z.literal('user-014'),
									z.literal('user-015'),
									z.literal('user-016'),
									z.literal('user-017'),
									z.literal('user-018'),
									z.literal('user-019'),
									z.literal('user-020'),
									z.literal('user-021'),
									z.literal('user-022'),
									z.literal('user-023'),
									z.literal('user-024'),
									z.literal('user-025'),
									z.literal('user-026'),
									z.literal('user-027'),
									z.literal('user-028'),
									z.literal('user-029'),
									z.literal('user-030'),
									z.literal('user-031'),
									z.literal('user-032'),
									z.literal('user-033'),
									z.literal('user-034'),
									z.literal('user-035'),
									z.literal('user-036'),
									z.literal('user-037'),
									z.literal('user-038'),
									z.literal('user-039'),
									z.literal('user-040'),
									z.literal('user-041'),
									z.literal('user-042'),
									z.literal('user-043'),
									z.literal('user-044'),
									z.literal('user-045'),
									z.literal('user-046'),
									z.literal('user-047'),
									z.literal('user-048'),
									z.literal('user-049'),
									z.literal('user-050'),
									z.literal('user-051'),
									z.literal('user-052'),
									z.literal('user-053'),
									z.literal('user-054'),
									z.literal('user-055'),
									z.literal('user-056'),
									z.literal('user-057'),
									z.literal('user-058'),
									z.literal('user-059'),
									z.literal('user-060'),
									z.literal('user-061'),
									z.literal('user-062'),
									z.literal('user-063'),
									z.literal('user-064'),
									z.literal('user-065'),
									z.literal('user-066'),
									z.literal('user-067'),
									z.literal('user-068'),
									z.literal('user-069'),
									z.literal('user-070'),
									z.literal('user-071'),
									z.literal('user-072'),
									z.literal('user-073'),
									z.literal('user-074'),
									z.literal('user-075'),
									z.literal('user-076'),
									z.literal('user-077'),
									z.literal('user-078'),
									z.literal('user-079'),
									z.literal('user-080'),
									z.literal('user-081'),
									z.literal('user-082'),
									z.literal('user-083'),
									z.literal('user-084'),
									z.literal('user-085'),
									z.literal('user-086'),
									z.literal('user-087'),
									z.literal('user-088'),
									z.literal('user-089'),
									z.literal('user-090'),
									z.literal('user-091'),
									z.literal('user-092'),
									z.literal('user-093'),
									z.literal('user-094'),
									z.literal('user-095'),
									z.literal('user-096'),
									z.literal('user-097'),
									z.literal('user-098'),
									z.literal('user-099'),
								]),
							}),
						}),
					}),
				),
				successResponse: HttpResponse.json({
					items: everyone.slice(100).map((username) => ({username})),
					page: {totalItems: 2},
				}),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);
		const {screen} = await renderPage({search: {pageSize: 100}});
		await expect.element(screen.getByRole('cell', {name: 'user-099'})).toBeVisible();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Users'}));

		// then
		await expect.element(screen.getByRole('option', {name: 'user-100'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'user-101'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: 'user-000'})).not.toBeInTheDocument();
	});

	it('should keep the user options in alphabetical order while users are picked', async ({worker}) => {
		// given
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockQueryUsersEndpoint({
				successResponse: HttpResponse.json({
					items: [{username: 'carol'}, {username: 'alice'}, {username: 'bob'}],
					page: {totalItems: 3},
				}),
			}),
		);
		const {screen} = await renderPage();

		// when
		await userEvent.click(screen.getByRole('button', {name: 'Assign user'}));
		await userEvent.click(screen.getByRole('combobox', {name: 'Users'}));
		await userEvent.click(screen.getByRole('option', {name: 'carol'}));

		// then
		const options = screen.getByRole('option');
		await expect.element(options).toHaveLength(3);
		await expect.element(options.nth(0)).toHaveTextContent('alice');
		await expect.element(options.nth(1)).toHaveTextContent('bob');
		await expect.element(options.nth(2)).toHaveTextContent('carol');
	});

	it('should unassign a user after confirmation', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane', 'zoe'])),
			}),
			mockUnassignUserFromTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);

		const {screen} = await renderPage();

		await userEvent.click(
			screen
				.getByRole('row')
				.filter({hasText: 'jane'})
				.getByRole('button', {name: /row actions/i}),
		);
		await userEvent.click(screen.getByRole('menuitem', {name: 'Remove user'}));
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane', 'zoe'])),
				delay: 'infinite',
			}),
		);
		await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Remove user'}));

		await expect.element(screen.getByText('Removed from tenant')).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'zoe'})).toBeVisible();
		await expect.element(screen.getByRole('cell', {name: 'jane'})).not.toBeInTheDocument();
	});

	it('should show the members the server confirms after an unassignment', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({
				successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['jane', 'zoe'])),
			}),
			mockUnassignUserFromTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
		);
		const {screen} = await renderPage();

		await userEvent.click(
			screen
				.getByRole('row')
				.filter({hasText: 'jane'})
				.getByRole('button', {name: /row actions/i}),
		);
		await userEvent.click(screen.getByRole('menuitem', {name: 'Remove user'}));
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse(['amy']))}),
		);
		await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Remove user'}));

		await expect.element(screen.getByRole('cell', {name: 'amy'})).toBeVisible();
	});

	it('should delete the tenant and notify the caller', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
			mockDeleteTenantEndpoint({successResponse: new HttpResponse(null, {status: 204})}),
			mockQueryTenantsEndpoint({successResponse: HttpResponse.json({items: [], page: {totalItems: 0}})}),
		);

		const {screen, onDeleted} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Delete'}));
		await userEvent.click(screen.getByRole('alertdialog').getByRole('button', {name: 'Delete'}));

		await expect.poll(() => onDeleted).toHaveBeenCalled();
	});

	it('should not offer edit or delete for the default tenant', async ({worker}) => {
		worker.use(
			mockQueryUsersByTenantEndpoint({successResponse: HttpResponse.json(createQueryUsersByTenantResponse())}),
		);

		const {screen} = await renderPage({tenant: createTenant({tenantId: '<default>', name: 'Default'})});

		await expect.element(screen.getByRole('heading', {name: 'Default'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Edit'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Delete'})).not.toBeInTheDocument();
	});
});
