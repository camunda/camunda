/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
import {keepPreviousData, useInfiniteQuery, useQueryClient} from '@tanstack/react-query';
import {useForm, useFormState} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import {useTranslation} from 'react-i18next';
import {IconButton, SearchInput, ToggleGroup, ToggleGroupItem} from '@camunda/design-system';
import {Pencil} from '@camunda/design-system/icons';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {PreviewDocumentButton} from '#/operate/shared/Documents/shadcn.components/PreviewDocumentButton';
import {DownloadDocumentButton} from '#/operate/shared/Documents/shadcn.components/DownloadDocumentButton';
import {InlineJsonEditor} from '#/operate/shared/Editors/shadcn.components/InlineJsonEditor';
import {StructuredList, StructuredRows} from '#/operate/shared/StructuredList/shadcn.components/StructuredList';
import {isRedactedVariable} from '#/shared/variables/redactedVariable';
import {useDebouncedValue} from '#/shared/hooks/useDebouncedValue';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {useProcessInstanceModification} from '../useProcessInstanceModification';
import {useVariableScope} from '../useVariableScope';
import {useCanEditVariables} from '../useCanEditVariables';
import {variableQuery, variablesQuery} from '../variables.queries';
import {needsMoreDocuments} from '../needsMoreDocuments';
import {ViewFullVariableButton} from './ViewFullVariableButton';
import {EditVariableModalButton} from './EditVariableModalButton';
import {ExistingVariableValue} from './ExistingVariableValue';
import {VariableEditButtons} from './VariableEditButtons';
import {NewVariableName} from './NewVariableName';
import {NewVariableValue} from './NewVariableValue';
import {NewVariableOperations} from './NewVariableOperations';
import {parseDocumentVariable} from '../parseDocumentVariable';
import {DocumentValueCell} from './DocumentValueCell';
import {ViewDocumentListButton} from './ViewDocumentListButton';
import {VariablesError} from './VariablesError';
import type {NewVariableFormValues, VariableFormValues} from '../variableFormValues';
import {cn} from '#/shared/cn';

const SEARCH_DEBOUNCE_MS = 300;
const VERTICAL_CELL_PADDING = '0.25rem';
const OPERATIONS_CLASS = 'flex min-w-10 justify-end';

const VariablesTable: React.FC<{scopeKey: string}> = ({scopeKey}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const form = useForm<VariableFormValues>();
	const {initialValues, submitting} = useFormState<VariableFormValues>();
	const {processInstanceId} = useProcessInstancePage();
	const {isModificationModeEnabled} = useProcessInstanceModification();
	const {canQuery, isVariableModificationAllowed} = useVariableScope();
	const canEditVariables = useCanEditVariables();
	const [searchValue, setSearchValue] = useState('');
	const [showDocumentsOnly, setShowDocumentsOnly] = useState(false);
	const debouncedSearchValue = useDebouncedValue(searchValue, SEARCH_DEBOUNCE_MS);
	const searchTerm = isModificationModeEnabled ? '' : debouncedSearchValue;
	const documentsOnly = showDocumentsOnly && !isModificationModeEnabled;
	const {data, error, isLoading, isPlaceholderData, hasNextPage, isFetchingNextPage, fetchNextPage} = useInfiniteQuery({
		...variablesQuery({
			processInstanceKey: processInstanceId,
			scopeKey: canQuery ? scopeKey : null,
			searchTerm,
			documentsOnly,
		}),
		placeholderData: keepPreviousData,
		refetchOnMount: false,
	});
	const pages = useMemo(
		() =>
			(data?.pages ?? []).map((page) =>
				page.items.map((variable) => {
					const isRedacted = isRedactedVariable(variable);
					return {
						...variable,
						isRedacted,
						documentResult: isRedacted ? null : parseDocumentVariable(variable.value, variable.isTruncated),
					};
				}),
			),
		[data],
	);
	const variables = pages.flat().filter(({documentResult}) => !documentsOnly || documentResult !== null);
	const hasSearchTerm = searchTerm.trim() !== '';
	const shouldLoadMoreDocuments =
		documentsOnly &&
		hasNextPage &&
		error === null &&
		needsMoreDocuments(pages.map((items) => items.filter(({documentResult}) => documentResult !== null).length));
	const canEditRow = !isModificationModeEnabled && canEditVariables;
	const isEditMode = (name: string) =>
		(initialValues?.name === name && canEditVariables) || isVariableModificationAllowed;

	useEffect(() => {
		if (shouldLoadMoreDocuments && !isFetchingNextPage && !isPlaceholderData) {
			void fetchNextPage();
		}
	}, [shouldLoadMoreDocuments, isFetchingNextPage, isPlaceholderData, fetchNextPage]);

	return (
		<>
			{!isModificationModeEnabled && (
				<div className="flex flex-col items-start gap-2 border-b border-border px-4 pb-4">
					<SearchInput
						size="sm"
						aria-label={t('operate.processInstance.variables.search')}
						placeholder={t('operate.processInstance.variables.search')}
						value={searchValue}
						onChange={(event) => setSearchValue(event.target.value)}
						onKeyDown={(event) => {
							if (event.key === 'Enter') {
								event.preventDefault();
							}
						}}
						onClear={() => setSearchValue('')}
					/>
					<ToggleGroup
						type="single"
						variant="outline"
						size="sm"
						aria-label={t('operate.processInstance.variables.filter')}
						value={showDocumentsOnly ? 'documents' : 'all'}
						onValueChange={(value) => {
							if (value !== '') {
								setShowDocumentsOnly(value === 'documents');
							}
						}}
					>
						<ToggleGroupItem value="all">{t('operate.processInstance.variables.filterAll')}</ToggleGroupItem>
						<ToggleGroupItem value="documents">
							{t('operate.processInstance.variables.filterDocuments')}
						</ToggleGroupItem>
					</ToggleGroup>
				</div>
			)}
			<div
				className={cn(
					'flex min-h-0 flex-1 flex-col transition-opacity duration-150',
					isPlaceholderData && 'pointer-events-none opacity-50',
				)}
			>
				{error !== null ? (
					<VariablesError error={error} />
				) : !isLoading && !shouldLoadMoreDocuments && (documentsOnly || hasSearchTerm) && variables.length === 0 ? (
					<div className="flex flex-1 items-center justify-center">
						<EmptyMessage
							message={t(
								hasSearchTerm
									? 'operate.processInstance.variables.noSearchResults'
									: 'operate.processInstance.variables.noDocuments',
							)}
						/>
					</div>
				) : (
					<StructuredList
						className="px-4 pb-4 [&_table]:table-fixed"
						label={t('operate.processInstance.variables.list')}
						headerSize="sm"
						verticalCellPadding={VERTICAL_CELL_PADDING}
						headerColumns={[
							{cellContent: t('operate.processInstance.variables.name'), width: '35%'},
							{cellContent: t('operate.processInstance.variables.value'), width: 'auto'},
							{
								cellContent: <span className="sr-only">{t('operate.processInstance.operations.actions')}</span>,
								width: '120px',
							},
						]}
						onVerticalScrollEndReach={() => {
							if (hasNextPage && !isFetchingNextPage) {
								void fetchNextPage();
							}
						}}
						dynamicRows={
							isVariableModificationAllowed ? (
								<FieldArray name="newVariables">
									{({fields}) => (
										<StructuredRows
											verticalCellPadding={VERTICAL_CELL_PADDING}
											rows={fields.map((fieldPrefix, index) => ({
												key: (fields.value[index] as NewVariableFormValues | undefined)?.id ?? fieldPrefix,
												columns: [
													{
														cellContent: <NewVariableName fieldPrefix={fieldPrefix} scopeKey={scopeKey} />,
														width: '35%',
													},
													{cellContent: <NewVariableValue fieldPrefix={fieldPrefix} />, width: 'auto'},
													{
														cellContent: (
															<NewVariableOperations
																fieldPrefix={fieldPrefix}
																scopeKey={scopeKey}
																onRemove={() => {
																	fields.remove(index);
																}}
															/>
														),
														width: '120px',
													},
												],
											}))}
										/>
									)}
								</FieldArray>
							) : undefined
						}
						rows={variables.map(({name, value, variableKey, isTruncated, isRedacted, documentResult}) => ({
							key: name,
							columns: [
								{
									cellContent: (
										<div title={name} className="my-1 overflow-hidden text-sm text-ellipsis whitespace-nowrap">
											{name}
										</div>
									),
									width: '35%',
								},
								{
									cellContent: isRedacted ? (
										<div className="py-1 font-mono text-[13px]/5 text-muted-foreground">
											{t('globalRedactedValueLabel')}
										</div>
									) : isEditMode(name) ? (
										<ExistingVariableValue
											variableKey={variableKey}
											variableName={name}
											variableValue={value}
											isTruncated={isTruncated}
										/>
									) : documentResult !== null ? (
										<DocumentValueCell result={documentResult} />
									) : (
										<InlineJsonEditor
											value={value}
											label={name}
											isTruncatedValue={isTruncated}
											readOnly
											onCopy={
												isTruncated
													? async () => (await queryClient.fetchQuery(variableQuery(variableKey))).value
													: undefined
											}
											renderButton={
												isTruncated
													? () => (
															<ViewFullVariableButton
																variableKey={variableKey}
																variableName={name}
																buttonLabel={t('operate.processInstance.variables.showAll')}
																canEdit={canEditRow}
															/>
														)
													: undefined
											}
										/>
									),
									width: 'auto',
								},
								{
									cellContent: isRedacted ? (
										<div className={OPERATIONS_CLASS} />
									) : (
										<div className={OPERATIONS_CLASS}>
											{isEditMode(name) ? (
												<EditVariableModalButton
													variableKey={variableKey}
													variableName={name}
													variableValue={value}
													isTruncated={isTruncated}
												/>
											) : (
												<ViewFullVariableButton variableKey={variableKey} variableName={name} canEdit={canEditRow} />
											)}
											{documentResult?.type === 'single' && (
												<>
													<PreviewDocumentButton document={documentResult.document} labelSuffix={name} />
													<DownloadDocumentButton document={documentResult.document} labelSuffix={name} />
												</>
											)}
											{documentResult?.type === 'list' && (
												<ViewDocumentListButton
													documents={documentResult.documents}
													isLowerBound={documentResult.isLowerBound}
													variableKey={variableKey}
													variableName={name}
												/>
											)}
											{documentResult === null &&
												canEditRow &&
												(initialValues?.name === name ? (
													<VariableEditButtons />
												) : (
													<IconButton
														type="button"
														variant="ghost"
														size="sm"
														tooltipSide="top"
														icon={Pencil}
														label={t('operate.processInstance.variables.edit', {name})}
														disabled={isFetchingNextPage || submitting}
														onClick={() => {
															form.reset({name, value, variableKey});
															form.change('value', value);
														}}
													/>
												))}
										</div>
									),
									width: '120px',
								},
							],
						}))}
					/>
				)}
			</div>
		</>
	);
};

export {VariablesTable};
