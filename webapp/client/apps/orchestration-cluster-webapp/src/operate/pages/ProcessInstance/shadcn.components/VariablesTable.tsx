/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TruncatedText} from '#/operate/shared/TruncatedText/shadcn.components/TruncatedText';
import {useEffect, useMemo, useState} from 'react';
import {keepPreviousData, useInfiniteQuery} from '@tanstack/react-query';
import {useForm, useFormState} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import {useTranslation} from 'react-i18next';
import {IconButton, SearchInput, Tabs, TabsContent, TabsList, TabsTrigger} from '@camunda/design-system';
import {Pencil} from '@camunda/design-system/icons';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {PreviewDocumentButton} from '#/operate/shared/Documents/shadcn.components/PreviewDocumentButton';
import {DownloadDocumentButton} from '#/operate/shared/Documents/shadcn.components/DownloadDocumentButton';
import {StructuredList, StructuredRows} from '#/operate/shared/StructuredList/shadcn.components/StructuredList';
import {beautifyJSON} from '#/shared/json/beautifyJSON';
import {beautifyTruncatedJSON} from '#/operate/shared/Editors/beautifyTruncatedJSON';
import {useDebouncedValue} from '#/shared/hooks/useDebouncedValue';
import {useProcessInstancePage} from '../useProcessInstancePage';
import {useProcessInstanceModification} from '../useProcessInstanceModification';
import {useVariableScope} from '../useVariableScope';
import {useCanEditVariables} from '../useCanEditVariables';
import {variablesQuery} from '../variables.queries';
import {needsMoreDocuments} from '../needsMoreDocuments';
import {ViewFullVariableButton} from './ViewFullVariableButton';
import {EditVariableModalButton} from './EditVariableModalButton';
import {VariableValueCell} from './VariableValueCell';
import {VariableEditButtons} from './VariableEditButtons';
import {parseDocumentVariable} from '../parseDocumentVariable';
import {ViewDocumentListButton} from './ViewDocumentListButton';
import {VariablesError} from './VariablesError';
import {NewVariableName} from './NewVariableName';
import {NewVariableValue} from './NewVariableValue';
import {NewVariableOperations} from './NewVariableOperations';
import type {NewVariableFormValues, VariableFormValues} from '../variableFormValues';
import {cn} from '#/shared/cn';

const SEARCH_DEBOUNCE_MS = 300;
const OPERATIONS_CLASS = 'flex min-w-10 justify-end';
const NAME_COLUMN_WIDTH = '35%';
const VALUE_COLUMN_WIDTH = 'auto';
const OPERATIONS_COLUMN_WIDTH = '120px';

const VariablesTable: React.FC<{scopeKey: string}> = ({scopeKey}) => {
	const {t} = useTranslation();
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
				page.items.map((variable) => ({
					...variable,
					documentResult: parseDocumentVariable(variable.value, variable.isTruncated),
				})),
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
	const isEmptyResult =
		!isLoading && !shouldLoadMoreDocuments && (documentsOnly || hasSearchTerm) && variables.length === 0;
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
				<div className="flex flex-col items-start gap-2 border-b border-border px-4 pt-1 pb-4 md:flex-row md:items-center">
					<SearchInput
						className="w-full md:w-auto md:min-w-0 md:flex-1"
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
					<Tabs
						value={showDocumentsOnly ? 'documents' : 'all'}
						onValueChange={(value) => setShowDocumentsOnly(value === 'documents')}
					>
						<TabsList variant="line" aria-label={t('operate.processInstance.variables.filter')}>
							<TabsTrigger value="all">{t('operate.processInstance.variables.filterAll')}</TabsTrigger>
							<TabsTrigger value="documents">{t('operate.processInstance.variables.filterDocuments')}</TabsTrigger>
						</TabsList>
						<TabsContent value={showDocumentsOnly ? 'documents' : 'all'} className="hidden" />
					</Tabs>
				</div>
			)}
			<div
				className={cn(
					'flex min-h-0 flex-1 flex-col transition-opacity duration-150',
					isPlaceholderData && 'pointer-events-none opacity-50',
				)}
			>
				{error !== null && <VariablesError error={error} />}
				{error === null && isEmptyResult && (
					<div className="flex flex-1 items-center justify-center">
						<EmptyMessage
							message={t(
								hasSearchTerm
									? 'operate.processInstance.variables.noSearchResults'
									: 'operate.processInstance.variables.noDocuments',
							)}
						/>
					</div>
				)}
				{error === null && !isEmptyResult && (
					<StructuredList
						className="[&_table]:min-w-[32rem] [&_table]:table-fixed"
						isFlush={false}
						cellClassName="align-middle"
						containerClassName="rounded-none border-0 shadow-none"
						label={t('operate.processInstance.variables.list')}
						headerColumns={[
							{cellContent: t('operate.processInstance.variables.name'), width: NAME_COLUMN_WIDTH},
							{cellContent: t('operate.processInstance.variables.value'), width: VALUE_COLUMN_WIDTH},
							{
								cellContent: <span className="sr-only">{t('operate.processInstance.operations.actions')}</span>,
								width: OPERATIONS_COLUMN_WIDTH,
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
											cellClassName="align-middle"
											rows={fields.map((fieldPrefix, index) => ({
												key: (fields.value[index] as NewVariableFormValues | undefined)?.id ?? fieldPrefix,
												dataTestId: `variable-${fieldPrefix}`,
												columns: [
													{
														cellContent: <NewVariableName fieldPrefix={fieldPrefix} scopeKey={scopeKey} />,
														width: NAME_COLUMN_WIDTH,
													},
													{cellContent: <NewVariableValue fieldPrefix={fieldPrefix} />, width: VALUE_COLUMN_WIDTH},
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
														width: OPERATIONS_COLUMN_WIDTH,
													},
												],
											}))}
										/>
									)}
								</FieldArray>
							) : undefined
						}
						rows={variables.map(({name, value, variableKey, isTruncated, documentResult}) => {
							const isMultiLine =
								documentResult === null &&
								(isTruncated ? beautifyTruncatedJSON(value) : beautifyJSON(value)).includes('\n');
							const multiLineAlignment = isMultiLine ? 'align-top' : undefined;

							return {
								key: name,
								columns: [
									{
										className: multiLineAlignment,
										cellContent: <TruncatedText text={name} className="py-1 text-sm" />,
										width: NAME_COLUMN_WIDTH,
									},
									{
										cellContent: (
											<VariableValueCell
												name={name}
												value={value}
												variableKey={variableKey}
												isTruncated={isTruncated}
												documentResult={documentResult}
												isEditing={isEditMode(name)}
												canEdit={canEditRow}
											/>
										),
										width: VALUE_COLUMN_WIDTH,
									},
									{
										className: multiLineAlignment,
										cellContent: (
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
										width: OPERATIONS_COLUMN_WIDTH,
									},
								],
							};
						})}
					/>
				)}
			</div>
		</>
	);
};

export {VariablesTable};
