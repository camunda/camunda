/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {useRouterState} from '@tanstack/react-router';
import type {DocumentReference, Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetDocumentEndpoint,
	mockGetElementInstanceEndpoint,
	mockGetVariableEndpoint,
	mockQueryElementInstancesEndpoint,
	mockQueryVariablesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {
	createElementInstance,
	createQueryElementInstancesResponse,
} from '#/shared-test-modules/api-mocks/element-instances';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {createPaginatedResponse, createProblemDetails} from '#/shared-test-modules/api-mocks/shared';
import {createDocumentReference} from '#/shared-test-modules/api-mocks/documents';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {ProcessInstanceVariables} from './ProcessInstanceVariables';
import {processInstanceSearchSchema} from './processInstanceSearch';
import {initialModificationState} from './modifications/modificationReducer';

const PROCESS_INSTANCE_ID = '2251799813685280';

afterEach(async () => {
	await cleanup();
	notificationsStore.reset();
});

function variablesResponse(
	items: Variable[],
	page?: {totalItems?: number; hasMoreTotalItems?: boolean; endCursor?: string},
) {
	return HttpResponse.json(createQueryVariablesByUserTaskResponse({items, page}));
}

function renderVariables(search = '', {isModificationModeEnabled: initialModificationMode = false} = {}) {
	function Variables() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		const [isModificationModeEnabled, setIsModificationModeEnabled] = useState(initialModificationMode);
		return (
			<div style={{height: '400px', display: 'flex', flexDirection: 'column'}}>
				<button type="button" onClick={() => setIsModificationModeEnabled(true)}>
					Enter modification mode
				</button>
				<ProcessInstancePageProvider
					processInstanceId={PROCESS_INSTANCE_ID}
					processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})}
					search={processInstanceSearchSchema.parse(routeSearch)}
					modification={{state: initialModificationState, isModificationModeEnabled, dispatch: () => {}}}
				>
					<ProcessInstanceVariables />
				</ProcessInstancePageProvider>
			</div>
		);
	}

	return renderWithRouter(Variables, {
		path: '/operate/processes/$processInstanceId/variables',
		initialEntry: `/operate/processes/${PROCESS_INSTANCE_ID}/variables${search}`,
	});
}

describe('<ProcessInstanceVariables />', () => {
	it('should show the process instance variables sorted by name', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				schema: z.object({
					filter: z
						.object({processInstanceKey: z.literal(PROCESS_INSTANCE_ID), scopeKey: z.literal(PROCESS_INSTANCE_ID)})
						.strict(),
					page: z.object({limit: z.literal(50)}).strict(),
					sort: z.tuple([z.object({field: z.literal('name'), order: z.literal('ASC')})]),
				}),
				successResponse: variablesResponse([
					createVariable({name: 'amount', value: '100', variableKey: '1'}),
					createVariable({name: 'customer', value: '{"name":"Jane"}', variableKey: '2'}),
				]),
				failureResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderVariables();

		await expect.element(screen.getByRole('button', {name: 'Copy amount'})).toHaveTextContent('100');
		await expect.element(screen.getByRole('button', {name: 'Copy customer'})).toHaveTextContent('{ "name": "Jane" }');
	});

	it('should show an empty message when the scope has no variables', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([])}));
		const screen = await renderVariables();

		await expect.element(screen.getByText('The element has no variables')).toBeVisible();
		await expect.element(screen.getByRole('searchbox')).not.toBeInTheDocument();
	});

	it('should show a permission message when variables are forbidden', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 403}), {status: 403}),
			}),
		);
		const screen = await renderVariables();

		await expect.element(screen.getByText('Missing permissions to access Variables')).toBeVisible();
	});

	it('should show an error message when variables cannot be fetched', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderVariables();

		await expect.element(screen.getByText('Variables could not be fetched')).toBeVisible();
		await expect.element(screen.getByText('Refresh the page to try again')).toBeVisible();
	});

	it('should ask for a single instance when the selected element has several', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 3)),
			}),
		);
		const screen = await renderVariables('?elementId=user-task');

		await expect
			.element(screen.getByText('To view the variables, select a single element instance in the instance history.'))
			.toBeVisible();
	});

	it('should ask for a single instance when the selected element count is only a lower bound', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({
						items: [createElementInstance()],
						page: {totalItems: 1, hasMoreTotalItems: true, startCursor: null, endCursor: null},
					}),
				),
			}),
		);
		const screen = await renderVariables('?elementId=user-task');

		await expect
			.element(screen.getByText('To view the variables, select a single element instance in the instance history.'))
			.toBeVisible();
	});

	it('should show an empty message for an element that has not run', async ({worker}) => {
		worker.use(
			mockQueryElementInstancesEndpoint({
				successResponse: HttpResponse.json(createQueryElementInstancesResponse([], 0)),
			}),
		);
		const screen = await renderVariables('?elementId=user-task');

		await expect.element(screen.getByText('The element has no variables')).toBeVisible();
	});

	it('should show an empty message for a placeholder instance', async () => {
		const screen = await renderVariables('?elementId=user-task&elementInstanceKey=1&isPlaceholder=true');

		await expect.element(screen.getByText('The element has no variables')).toBeVisible();
	});

	it('should scope variables to the selected element instance', async ({worker}) => {
		const elementInstance = createElementInstance({elementInstanceKey: '2251799813685299'});
		worker.use(
			mockGetElementInstanceEndpoint({successResponse: HttpResponse.json(elementInstance)}),
			mockQueryVariablesEndpoint({
				schema: z.object({filter: z.object({scopeKey: z.literal(elementInstance.elementInstanceKey)})}),
				successResponse: variablesResponse([createVariable({name: 'local'})]),
				failureResponse: variablesResponse([]),
			}),
		);
		const screen = await renderVariables(
			`?elementId=user-task&elementInstanceKey=${elementInstance.elementInstanceKey}`,
		);

		await expect.element(screen.getByTestId('variable-local')).toBeVisible();
	});

	it('should search variables by name', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}));
		const screen = await renderVariables();
		await expect.element(screen.getByTestId('variable-amount')).toBeVisible();

		worker.use(
			mockQueryVariablesEndpoint({
				schema: z.object({filter: z.object({name: z.object({$like: z.literal('*cust*')})})}),
				successResponse: variablesResponse([]),
				failureResponse: variablesResponse([createVariable({name: 'amount'})]),
			}),
		);
		await userEvent.fill(screen.getByRole('searchbox', {name: 'Search variables by name'}), ' cust ');

		await expect.element(screen.getByText('No variables match your search')).toBeVisible();

		await expect.element(screen.getByRole('searchbox', {name: 'Search variables by name'})).toBeVisible();
	});

	it('should show an error when searching variables fails', async ({worker}) => {
		worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}));
		const screen = await renderVariables();
		await expect.element(screen.getByTestId('variable-amount')).toBeVisible();

		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		await userEvent.fill(screen.getByRole('searchbox', {name: 'Search variables by name'}), 'cust');

		await expect.element(screen.getByText('Variables could not be fetched')).toBeVisible();
		await expect.element(screen.getByText('No variables match your search')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('searchbox', {name: 'Search variables by name'})).toBeVisible();
	});

	it('should show and copy the full value of a truncated variable', async ({worker}) => {
		vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([
					createVariable({name: 'payload', value: '"abc', isTruncated: true, variableKey: '7'}),
				]),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'payload', value: '"abcdef"', variableKey: '7'})),
			}),
		);
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Show all'}));

		const dialog = screen.getByRole('dialog', {name: 'Full value of payload'});
		await expect.element(dialog).toBeVisible();
		await userEvent.click(dialog.getByRole('button', {name: 'Close'}).first());
		await expect.element(dialog).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Copy payload'}));

		await expect.poll(() => vi.mocked(navigator.clipboard.writeText).mock.calls).toEqual([['"abcdef"']]);
	});

	it('should open the full value of a variable', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([createVariable({name: 'amount', value: '100', variableKey: '9'})]),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createVariable({name: 'amount', value: '100', variableKey: '9'})),
			}),
		);
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Open'}));

		const dialog = screen.getByRole('dialog', {name: 'Full value of amount'});
		await expect.element(dialog).toBeVisible();
		await userEvent.click(dialog.getByRole('button', {name: 'Close'}).first());
		await expect.element(dialog).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Open'})).toHaveFocus();
	});

	it('should notify when the full value cannot be fetched', async ({worker}) => {
		worker.use(
			mockQueryVariablesEndpoint({
				successResponse: variablesResponse([createVariable({name: 'amount', variableKey: '9'})]),
			}),
			mockGetVariableEndpoint({
				successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
			}),
		);
		const screen = await renderVariables();

		await userEvent.click(screen.getByRole('button', {name: 'Open'}));

		await expect
			.poll(() => notificationsStore.notifications.map(({title}) => title))
			.toContain('Failed to fetch full amount');
		await expect.element(screen.getByRole('button', {name: 'Open'})).toHaveFocus();
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
	});

	describe('document variables', () => {
		const documentReference = (documentId: string, metadata?: Partial<DocumentReference['metadata']>) =>
			createDocumentReference({documentId, metadata: {fileName: `${documentId}.pdf`, ...metadata}});
		const documentListValue = JSON.stringify([
			documentReference('doc-1'),
			documentReference('doc-2'),
			documentReference('doc-3'),
		]);
		const truncatedDocumentListValue = documentListValue.slice(0, documentListValue.lastIndexOf('"documentId"'));
		const decoyVariables = [
			createVariable({name: 'nestedDecoy', value: JSON.stringify({wrapper: {'camunda.document.type': 'camunda'}})}),
			createVariable({name: 'stringDecoy', value: JSON.stringify('attach your camunda.document.type here')}),
		];
		const documentsOnlySchema = z.object({
			filter: z.object({value: z.object({$like: z.literal('*camunda.document.type*')})}),
		});

		it('should show a single document with its preview and download actions', async ({worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({
					successResponse: variablesResponse([
						createVariable({
							name: 'invoice',
							value: JSON.stringify(documentReference('doc-1', {fileName: 'invoice.pdf', size: 1536})),
						}),
					]),
				}),
			);
			const screen = await renderVariables();

			const row = screen.getByTestId('variable-invoice');
			await expect.element(row.getByTestId('document-value-cell')).toHaveTextContent('invoice.pdf1.5 KiB');
			await expect.element(row.getByRole('button', {name: 'Preview'})).toBeEnabled();
			await expect
				.element(row.getByRole('link', {name: 'Download'}))
				.toHaveAttribute(
					'href',
					`${window.location.origin}/v2/documents/doc-1?storeId=in-memory&contentHash=sha256-xyz`,
				);
			await expect.element(row.getByRole('button', {name: 'Open'})).toBeVisible();
			await expect.element(row.getByRole('button', {name: 'View documents'})).not.toBeInTheDocument();
			await expect.element(row.getByRole('button', {name: 'Copy invoice'})).not.toBeInTheDocument();
		});

		it('should mark an expired document and disable its actions', async ({worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({
					successResponse: variablesResponse([
						createVariable({
							name: 'invoice',
							value: JSON.stringify(documentReference('doc-1', {expiresAt: '2000-01-01T00:00:00.000Z'})),
						}),
					]),
				}),
			);
			const screen = await renderVariables();

			const row = screen.getByTestId('variable-invoice');
			await expect.element(row.getByText('Expired')).toBeVisible();
			await expect.element(row.getByRole('button', {name: 'Preview'})).toBeDisabled();
			await expect.element(row.getByRole('button', {name: 'Download'})).toBeDisabled();
		});

		it('should list the documents of a document list variable', async ({worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({
					successResponse: variablesResponse([createVariable({name: 'files', value: documentListValue})]),
				}),
			);
			const screen = await renderVariables();

			const row = screen.getByTestId('variable-files');
			await expect.element(row.getByTestId('document-value-cell')).toHaveTextContent('3 documents');
			await expect.element(row.getByRole('button', {name: 'Preview'})).not.toBeInTheDocument();
			await expect.element(row.getByRole('link', {name: 'Download'})).not.toBeInTheDocument();

			await userEvent.click(row.getByRole('button', {name: 'View documents'}));

			const dialog = screen.getByRole('dialog', {name: '3 documents in files'});
			await expect.element(dialog.getByRole('listitem', {name: 'doc-1.pdf'})).toBeVisible();
			await expect.element(dialog.getByRole('listitem', {name: 'doc-2.pdf'})).toBeVisible();
			await expect.element(dialog.getByRole('listitem', {name: 'doc-3.pdf'})).toBeVisible();
		});

		it('should load all documents of a truncated document list variable', async ({worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({
					successResponse: variablesResponse([
						createVariable({name: 'files', value: truncatedDocumentListValue, isTruncated: true, variableKey: '5'}),
					]),
				}),
				mockGetVariableEndpoint({
					successResponse: HttpResponse.json(
						createVariable({name: 'files', value: documentListValue, variableKey: '5'}),
					),
				}),
			);
			const screen = await renderVariables();

			const row = screen.getByTestId('variable-files');
			await expect.element(row.getByTestId('document-value-cell')).toHaveTextContent('2+ documents');

			await userEvent.click(row.getByRole('button', {name: 'View documents'}));

			const dialog = screen.getByRole('dialog', {name: '3 documents in files'});
			await expect.element(dialog.getByRole('listitem', {name: 'doc-3.pdf'})).toBeVisible();
		});

		it('should keep the truncated documents when the full document list cannot be loaded', async ({worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({
					successResponse: variablesResponse([
						createVariable({name: 'files', value: truncatedDocumentListValue, isTruncated: true, variableKey: '5'}),
					]),
				}),
				mockGetVariableEndpoint({
					successResponse: HttpResponse.json(createProblemDetails({status: 500}), {status: 500}),
				}),
			);
			const screen = await renderVariables();

			await userEvent.click(screen.getByTestId('variable-files').getByRole('button', {name: 'View documents'}));

			const dialog = screen.getByRole('dialog', {name: '2+ documents in files'});
			await expect
				.element(
					dialog.getByText('Failed to load the full variable value. More documents may exist for this variable.'),
				)
				.toBeVisible();
			await expect.element(dialog.getByRole('listitem', {name: 'doc-2.pdf'})).toBeVisible();
		});

		it('should render values that only mention the document marker as regular variables', async ({worker}) => {
			worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse(decoyVariables)}));
			const screen = await renderVariables();

			const row = screen.getByTestId('variable-nestedDecoy');
			await expect.element(row.getByRole('button', {name: 'Copy nestedDecoy'})).toBeVisible();
			await expect.element(row.getByTestId('document-value-cell')).not.toBeInTheDocument();
			await expect.element(row.getByRole('button', {name: 'Preview'})).not.toBeInTheDocument();
			await expect.element(row.getByRole('button', {name: 'View documents'})).not.toBeInTheDocument();
		});

		it('should filter the variables by documents', async ({worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({
					schema: documentsOnlySchema,
					successResponse: variablesResponse([
						createVariable({name: 'invoice', value: JSON.stringify(documentReference('doc-1'))}),
						...decoyVariables,
					]),
					failureResponse: variablesResponse([
						createVariable({name: 'amount'}),
						createVariable({name: 'invoice', value: JSON.stringify(documentReference('doc-1'))}),
					]),
				}),
			);
			const screen = await renderVariables();
			await expect.element(screen.getByTestId('variable-amount')).toBeVisible();
			await expect.element(screen.getByRole('button', {name: 'All'})).toHaveAttribute('aria-pressed', 'true');

			await userEvent.click(screen.getByRole('button', {name: 'Documents'}));

			await expect.element(screen.getByRole('button', {name: 'Documents'})).toHaveAttribute('aria-pressed', 'true');
			await expect.element(screen.getByTestId('variable-amount')).not.toBeInTheDocument();
			await expect.element(screen.getByTestId('variable-invoice')).toBeVisible();
			await expect.element(screen.getByTestId('variable-nestedDecoy')).not.toBeInTheDocument();
			await expect.element(screen.getByTestId('variable-stringDecoy')).not.toBeInTheDocument();

			await userEvent.click(screen.getByRole('button', {name: 'All'}));

			await expect.element(screen.getByTestId('variable-amount')).toBeVisible();
			await expect.element(screen.getByTestId('variable-invoice')).toBeVisible();
		});

		it('should show an empty message when there are no document variables', async ({worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({
					schema: documentsOnlySchema,
					successResponse: variablesResponse(decoyVariables),
					failureResponse: variablesResponse([createVariable({name: 'amount'})]),
				}),
			);
			const screen = await renderVariables();
			await expect.element(screen.getByTestId('variable-amount')).toBeVisible();

			await userEvent.click(screen.getByRole('button', {name: 'Documents'}));

			await expect.element(screen.getByText('There are no document variables')).toBeVisible();
			await expect.element(screen.getByTestId('variable-nestedDecoy')).not.toBeInTheDocument();
		});

		it('should hide the search and document filter in modification mode', async ({worker}) => {
			worker.use(mockQueryVariablesEndpoint({successResponse: variablesResponse([createVariable({name: 'amount'})])}));
			const screen = await renderVariables('', {isModificationModeEnabled: true});

			await expect.element(screen.getByTestId('variable-amount')).toBeVisible();
			await expect.element(screen.getByRole('group', {name: 'Variable filter'})).not.toBeInTheDocument();
			await expect.element(screen.getByRole('searchbox')).not.toBeInTheDocument();
		});

		it('should stop filtering by documents when entering modification mode', async ({worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({
					schema: documentsOnlySchema,
					successResponse: variablesResponse([
						createVariable({name: 'invoice', value: JSON.stringify(documentReference('doc-1'))}),
					]),
					failureResponse: variablesResponse([
						createVariable({name: 'amount'}),
						createVariable({name: 'invoice', value: JSON.stringify(documentReference('doc-1'))}),
					]),
				}),
			);
			const screen = await renderVariables();
			await expect.element(screen.getByTestId('variable-amount')).toBeVisible();
			await userEvent.click(screen.getByRole('button', {name: 'Documents'}));
			await expect.element(screen.getByTestId('variable-amount')).not.toBeInTheDocument();

			await userEvent.click(screen.getByRole('button', {name: 'Enter modification mode'}));

			await expect.element(screen.getByTestId('variable-amount')).toBeVisible();
			await expect.element(screen.getByTestId('variable-invoice')).toBeVisible();
			await expect.element(screen.getByRole('group', {name: 'Variable filter'})).not.toBeInTheDocument();
		});

		it('should keep loading document candidates when a page has no documents', async ({worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({
					schema: z.object({page: z.object({after: z.literal('cursor-1')})}),
					successResponse: variablesResponse([
						createVariable({name: 'invoice', value: JSON.stringify(documentReference('doc-1'))}),
					]),
					failureResponse: variablesResponse(
						Array.from({length: 50}, (_, index) =>
							createVariable({
								name: `decoy-${String(index).padStart(2, '0')}`,
								value: JSON.stringify('mentions camunda.document.type'),
								variableKey: `${index}`,
							}),
						),
						{totalItems: 50, hasMoreTotalItems: true, endCursor: 'cursor-1'},
					),
				}),
			);
			const screen = await renderVariables();
			await expect.element(screen.getByTestId('variable-decoy-00')).toBeVisible();

			await userEvent.click(screen.getByRole('button', {name: 'Documents'}));

			await expect.element(screen.getByTestId('variable-invoice')).toBeVisible();
			await expect.element(screen.getByTestId('variable-decoy-00')).not.toBeInTheDocument();
			await expect.element(screen.getByText('There are no document variables')).not.toBeInTheDocument();
		});

		it('should list the documents of a complete list instead of a previously fetched value', async ({worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({
					successResponse: variablesResponse([
						createVariable({name: 'files', value: documentListValue, variableKey: '5'}),
					]),
				}),
				mockGetVariableEndpoint({
					successResponse: HttpResponse.json(
						createVariable({
							name: 'files',
							value: JSON.stringify([documentReference('old-1'), documentReference('old-2')]),
							variableKey: '5',
						}),
					),
				}),
			);
			const screen = await renderVariables();
			const row = screen.getByTestId('variable-files');
			await userEvent.click(row.getByRole('button', {name: 'Open'}));
			const fullValue = screen.getByRole('dialog', {name: 'Full value of files'});
			await expect.element(fullValue).toBeVisible();
			await userEvent.click(fullValue.getByRole('button', {name: 'Close'}).first());
			await expect.element(fullValue).not.toBeInTheDocument();

			await userEvent.click(row.getByRole('button', {name: 'View documents'}));

			const dialog = screen.getByRole('dialog', {name: '3 documents in files'});
			await expect.element(dialog.getByRole('listitem', {name: 'doc-3.pdf'})).toBeVisible();
			await expect.element(dialog.getByRole('listitem', {name: 'old-1.pdf'})).not.toBeInTheDocument();
		});

		it('should preview a document from the variables list', async ({worker}) => {
			worker.use(
				mockQueryVariablesEndpoint({
					successResponse: variablesResponse([
						createVariable({
							name: 'invoice',
							value: JSON.stringify(documentReference('doc-pdf', {fileName: 'invoice.pdf'})),
						}),
					]),
				}),
				mockGetDocumentEndpoint({
					successResponse: new HttpResponse('%PDF-1.4', {headers: {'Content-Type': 'application/pdf'}}),
				}),
			);
			const screen = await renderVariables();

			await userEvent.click(screen.getByTestId('variable-invoice').getByRole('button', {name: 'Preview'}));

			const dialog = screen.getByRole('dialog', {name: 'Preview: invoice.pdf'});
			await expect.element(dialog.getByTitle('invoice.pdf')).toBeVisible();
			await userEvent.click(dialog.getByRole('button', {name: 'Close'}));
			await expect.element(dialog).not.toBeInTheDocument();
			await expect.element(screen.getByTestId('variable-invoice')).toBeVisible();
		});
	});
});
