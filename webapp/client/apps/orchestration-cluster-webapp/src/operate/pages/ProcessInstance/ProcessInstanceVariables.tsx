/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {keepPreviousData, useInfiniteQuery, useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Loading, Search} from '@carbon/react';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {FilterSwitcher, FilterSwitcherButton} from '#/operate/shared/FilterSwitcher/styled';
import {PreviewDocumentButton} from '#/operate/shared/Documents/PreviewDocumentButton';
import {DownloadDocumentButton} from '#/operate/shared/Documents/DownloadDocumentButton';
import {InlineJsonEditor} from '#/operate/shared/Editors/InlineJsonEditor';
import {ForbiddenError} from '#/shared/errors';
import {useDebouncedValue} from '#/shared/hooks/useDebouncedValue';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {variableQuery, variablesQuery} from './variables.queries';
import {ViewFullVariableButton} from './ViewFullVariableButton';
import {parseDocumentVariable} from './parseDocumentVariable';
import {DocumentValueCell} from './DocumentValueCell';
import {ViewDocumentListButton} from './ViewDocumentListButton';
import {
	Content,
	DimmableResults,
	EmptyMessageContainer,
	FilterSwitcherContainer,
	Operations,
	StructuredList,
	VariableName,
	VariablesSearch,
} from './variables.styled';

const SEARCH_DEBOUNCE_MS = 300;

const VariablesError: React.FC<{error: Error | null}> = ({error}) => {
	const {t} = useTranslation();
	const isForbidden = error instanceof ForbiddenError;

	return (
		<EmptyMessageContainer>
			<ErrorMessage
				message={t(
					isForbidden ? 'operate.processInstance.variables.forbidden' : 'operate.processInstance.variables.error',
				)}
				additionalInfo={t(
					isForbidden
						? 'operate.processInstance.variables.forbiddenInfo'
						: 'operate.shared.errorMessage.additionalInfo',
				)}
			/>
		</EmptyMessageContainer>
	);
};

const VariablesTable: React.FC<{processInstanceKey: string; scopeKey: string}> = ({processInstanceKey, scopeKey}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const {isModificationModeEnabled} = useProcessInstanceModification();
	const [searchValue, setSearchValue] = useState('');
	const [showDocumentsOnly, setShowDocumentsOnly] = useState(false);
	const searchTerm = useDebouncedValue(searchValue, SEARCH_DEBOUNCE_MS);
	const {data, error, isLoading, isPlaceholderData, hasNextPage, isFetchingNextPage, fetchNextPage} = useInfiniteQuery({
		...variablesQuery({processInstanceKey, scopeKey, searchTerm, documentsOnly: showDocumentsOnly}),
		placeholderData: keepPreviousData,
		refetchOnMount: false,
	});
	const variables = useMemo(
		() =>
			(data?.pages.flatMap((page) => page.items) ?? [])
				.map((variable) => ({...variable, documentResult: parseDocumentVariable(variable.value, variable.isTruncated)}))
				.filter(({documentResult}) => !showDocumentsOnly || documentResult !== null),
		[data, showDocumentsOnly],
	);
	const hasSearchTerm = searchTerm.trim() !== '';

	return (
		<>
			<FilterSwitcherContainer>
				<VariablesSearch>
					<Search
						size="sm"
						labelText={t('operate.processInstance.variables.search')}
						placeholder={t('operate.processInstance.variables.search')}
						value={searchValue}
						onChange={(event) => setSearchValue(event.target.value)}
						onClear={() => setSearchValue('')}
					/>
				</VariablesSearch>
				{!isModificationModeEnabled && (
					<FilterSwitcher role="group" aria-label={t('operate.processInstance.variables.filter')}>
						<FilterSwitcherButton
							type="button"
							aria-pressed={!showDocumentsOnly}
							onClick={() => setShowDocumentsOnly(false)}
						>
							{t('operate.processInstance.variables.filterAll')}
						</FilterSwitcherButton>
						<FilterSwitcherButton
							type="button"
							aria-pressed={showDocumentsOnly}
							onClick={() => setShowDocumentsOnly(true)}
						>
							{t('operate.processInstance.variables.filterDocuments')}
						</FilterSwitcherButton>
					</FilterSwitcher>
				)}
			</FilterSwitcherContainer>
			<DimmableResults $dimmed={isPlaceholderData}>
				{error !== null ? (
					<VariablesError error={error} />
				) : !isLoading && (showDocumentsOnly || hasSearchTerm) && variables.length === 0 ? (
					<EmptyMessageContainer>
						<EmptyMessage
							message={t(
								hasSearchTerm
									? 'operate.processInstance.variables.noSearchResults'
									: 'operate.processInstance.variables.noDocuments',
							)}
						/>
					</EmptyMessageContainer>
				) : (
					<StructuredList
						dataTestId="variables-list"
						label={t('operate.processInstance.variables.list')}
						headerSize="sm"
						verticalCellPadding="var(--cds-spacing-02)"
						headerColumns={[
							{cellContent: t('operate.processInstance.variables.name'), width: '35%'},
							{cellContent: t('operate.processInstance.variables.value'), width: 'auto'},
							{
								cellContent: (
									<span className="cds--visually-hidden">{t('operate.processInstance.operations.actions')}</span>
								),
								width: '120px',
							},
						]}
						onVerticalScrollEndReach={() => {
							if (hasNextPage && !isFetchingNextPage) {
								void fetchNextPage();
							}
						}}
						rows={variables.map(({name, value, variableKey, isTruncated, documentResult}) => ({
							key: name,
							dataTestId: `variable-${name}`,
							columns: [
								{cellContent: <VariableName title={name}>{name}</VariableName>, width: '35%'},
								{
									cellContent:
										documentResult !== null ? (
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
																/>
															)
														: undefined
												}
											/>
										),
									width: 'auto',
								},
								{
									cellContent: (
										<Operations>
											<ViewFullVariableButton variableKey={variableKey} variableName={name} />
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
										</Operations>
									),
									width: '120px',
								},
							],
						}))}
					/>
				)}
			</DimmableResults>
		</>
	);
};

const ProcessInstanceVariables: React.FC = () => {
	const {t} = useTranslation();
	const {processInstanceId} = useProcessInstancePage();
	const {
		hasSelection,
		selectedElementInstanceKey,
		resolvedElementInstance,
		selectedElementInstanceCount,
		isSelectedInstancePlaceholder,
		isFetchingElementError,
	} = useProcessInstanceElementSelection();
	const hasNoInstances = hasSelection && selectedElementInstanceKey === null && selectedElementInstanceCount === 0;
	const hasMultipleInstances =
		selectedElementInstanceKey === null &&
		selectedElementInstanceCount !== null &&
		selectedElementInstanceCount > 0 &&
		resolvedElementInstance === null;
	const scopeKey = hasSelection
		? (selectedElementInstanceKey ?? resolvedElementInstance?.elementInstanceKey ?? null)
		: processInstanceId;
	const canQuery = !hasNoInstances && !hasMultipleInstances && !isSelectedInstancePlaceholder;
	const {data, error, isError, isLoading} = useInfiniteQuery(
		variablesQuery({processInstanceKey: processInstanceId, scopeKey: canQuery ? scopeKey : null}),
	);

	if ((canQuery && isError) || isFetchingElementError) {
		return <VariablesError error={error} />;
	}

	if (hasMultipleInstances) {
		return (
			<EmptyMessageContainer>
				<EmptyMessage message={t('operate.processInstance.variables.multiInstance')} />
			</EmptyMessageContainer>
		);
	}

	if (hasNoInstances || isSelectedInstancePlaceholder || (data !== undefined && data.pages[0]?.items.length === 0)) {
		return (
			<EmptyMessageContainer>
				<EmptyMessage message={t('operate.processInstance.variables.empty')} />
			</EmptyMessageContainer>
		);
	}

	return (
		<Content>
			{(scopeKey === null || isLoading) && <Loading data-testid="variables-spinner" />}
			{scopeKey !== null && data !== undefined && (
				<VariablesTable key={scopeKey} processInstanceKey={processInstanceId} scopeKey={scopeKey} />
			)}
		</Content>
	);
};

export {ProcessInstanceVariables};
