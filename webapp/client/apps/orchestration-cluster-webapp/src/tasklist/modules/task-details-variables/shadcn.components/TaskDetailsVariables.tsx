/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	Button,
	EmptyState,
	Heading,
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
	toast,
	Tooltip,
	TooltipContent,
	TooltipTrigger,
} from '@camunda/design-system';
import type {QueryVariablesByUserTaskResponseBody, Variable} from '@camunda/camunda-api-zod-schemas/8.10';
import {type InfiniteData, useQueryClient} from '@tanstack/react-query';
import arrayMutators from 'final-form-arrays';
import get from 'lodash/get';
import intersection from 'lodash/intersection';
import {CircleAlert, Plus} from 'lucide-react';
import {lazy, Suspense, useCallback, useMemo, useRef, useState} from 'react';
import {Form} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {queries} from '#/shared/http/queries';
import {CompleteTaskButton} from '#/tasklist/modules/task-details/shadcn.components/CompleteTaskButton';
import type {CompletionStatus} from '#/tasklist/modules/task-details/useTaskCompletion';
import type {VariablesFormValues} from '#/tasklist/modules/task-details-variables/types';
import {
	createVariableFieldName,
	getVariableFieldName,
} from '#/tasklist/modules/task-details-variables/variableFieldNames';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';
import {ResetForm} from './ResetForm';
import {VariableEditor} from './VariableEditor';

const JSONEditorModal = lazy(async () => {
	const [{loadMonaco}, {JSONEditorModal}] = await Promise.all([
		import('#/tasklist/modules/task-details-variables/loadMonaco'),
		import('#/tasklist/modules/task-details-variables/shadcn.components/JSONEditorModal'),
	]);
	loadMonaco();
	return {default: JSONEditorModal};
});

type Props = {
	userTaskKey: string;
	variables: Variable[];
	totalVariables: number;
	isEditingAllowed: boolean;
	isCompletionAllowed: boolean;
	isCompleted: boolean;
	completionStatus: CompletionStatus;
	isCompletionHidden: boolean;
	hasNextPage: boolean;
	isFetchingNextPage: boolean;
	isNextPageError: boolean;
	onLoadNextPage: () => void;
	onSubmit: (variables: Record<string, unknown>) => void;
};

const TaskDetailsVariables: React.FC<Props> = ({
	userTaskKey,
	variables,
	totalVariables,
	isEditingAllowed,
	isCompletionAllowed,
	isCompleted,
	completionStatus,
	isCompletionHidden,
	hasNextPage,
	isFetchingNextPage,
	isNextPageError,
	onLoadNextPage,
	onSubmit,
}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const formRef = useRef<HTMLFormElement | null>(null);
	const scrollContainerRef = useRef<HTMLDivElement | null>(null);
	const [variablesLoadingFullValue, setVariablesLoadingFullValue] = useState<string[]>([]);
	const [editingVariable, setEditingVariable] = useState<string | undefined>();
	const initialFormValues = useMemo(
		() => Object.fromEntries(variables.map((variable) => [createVariableFieldName(variable.name), variable.value])),
		[variables],
	);
	const isJsonEditorModalOpen = editingVariable !== undefined;
	const isCompletionBusy = completionStatus === 'active';

	const fetchFullVariable = useCallback(
		async (variableKey: string) => {
			setVariablesLoadingFullValue((variableKeys) => [...variableKeys, variableKey]);
			try {
				const variable = await queryClient.query(queries.getVariable(variableKey));
				queryClient.setQueryData<InfiniteData<QueryVariablesByUserTaskResponseBody>>(
					queries.queryAllVariablesByUserTask(userTaskKey).queryKey,
					(current) =>
						current === undefined
							? current
							: {
									...current,
									pages: current.pages.map((page) => ({
										...page,
										items: page.items.map((oldVariable) =>
											oldVariable.variableKey === variableKey ? {...variable, isTruncated: false} : oldVariable,
										),
									})),
								},
				);
			} catch {
				toast.error(t('tasklist.variablesFullValueFetchError'), {duration: 5000});
			} finally {
				setVariablesLoadingFullValue((variableKeys) => variableKeys.filter((key) => key !== variableKey));
			}
		},
		[queryClient, t, userTaskKey],
	);

	return (
		<Form<VariablesFormValues>
			mutators={{...arrayMutators}}
			initialValues={initialFormValues}
			keepDirtyOnReinitialize
			onSubmit={(values, form) => {
				const {dirtyFields = {}, initialValues = {}} = form.getState();
				const existingVariables = intersection(Object.keys(initialValues), Object.keys(dirtyFields)).reduce<
					Record<string, unknown>
				>((acc, name) => {
					acc[getVariableFieldName(name)] =
						typeof values[name] === 'string' ? tryParseJSON(values[name]) : values[name];
					return acc;
				}, {});
				const newVariables = (get(values, 'newVariables') || []).reduce<Record<string, unknown>>(
					(acc, {name, value}) => {
						acc[name] = tryParseJSON(value);
						return acc;
					},
					{},
				);

				onSubmit({...existingVariables, ...newVariables});
			}}
		>
			{({form, handleSubmit, values, validating, submitting, hasValidationErrors}) => {
				const hasEmptyNewVariable = values.newVariables?.some((variable) => variable === undefined) ?? false;
				const isEmpty = variables.length === 0 && (values.newVariables?.length ?? 0) === 0;

				return (
					<div className="flex h-full min-h-0 w-full flex-col gap-2">
						<div className="mt-4 flex min-h-8 w-full items-center px-4">
							<Heading as="h2" variant="heading-lg" className="sr-only">
								{t('tasklist.variablesTitle')}
							</Heading>
							{isCompleted ? null : (
								<Button
									className="ml-auto"
									variant="secondary"
									type="button"
									size="sm"
									onClick={() => form.mutators.push?.('newVariables')}
									disabled={!isEditingAllowed || isCompletionBusy}
									title={isEditingAllowed ? undefined : t('tasklist.variablesAddVariableTooltip')}
								>
									<Plus aria-hidden />
									{t('tasklist.taskVariablesAddVariable')}
								</Button>
							)}
						</div>
						<div
							className="min-h-0 w-full flex-1 overflow-auto scroll-smooth"
							ref={scrollContainerRef}
							data-testid="variables-scroll-container"
							onScroll={(event) => {
								const {scrollHeight, scrollTop, clientHeight} = event.currentTarget;
								if (Math.floor(scrollHeight - clientHeight - scrollTop) <= 0) {
									onLoadNextPage();
								}
							}}
						>
							<form className="h-full w-full" onSubmit={handleSubmit} data-testid="variables-table" ref={formRef}>
								<ResetForm isAssigned={isEditingAllowed} />
								<div
									className="flex h-full flex-col flex-nowrap items-center justify-between"
									tabIndex={-1}
									data-testid="task-tab-content"
								>
									<div className="w-full px-4 pb-4" data-testid="variables-form-table">
										{isEmpty ? (
											<Table size="md" className="table-fixed">
												<TableHeader>
													<TableRow>
														<TableHead className="w-50">{t('tasklist.variableEditorVariableNameHeader')}</TableHead>
														<TableHead>{t('tasklist.variableEditorVariableValueHeader')}</TableHead>
														<TableHead className="w-20">
															<span className="sr-only">{t('tasklist.variableEditorOpenJsonLabel')}</span>
														</TableHead>
													</TableRow>
												</TableHeader>
												<TableBody>
													<TableRow>
														<TableCell className="h-32" colSpan={3}>
															<EmptyState
																size="sm"
																heading={t('tasklist.variablesNoVariablesHeading')}
																description={isCompleted ? '' : t('tasklist.variablesClickOnAddVariablesPrompt')}
															/>
														</TableCell>
													</TableRow>
												</TableBody>
											</Table>
										) : (
											<VariableEditor
												containerRef={formRef}
												variables={variables}
												totalVariables={totalVariables}
												readOnly={!isEditingAllowed}
												isDisabled={isCompletionBusy}
												fetchFullVariable={fetchFullVariable}
												variablesLoadingFullValue={variablesLoadingFullValue}
												onMaximizeClick={(fieldName) => setEditingVariable(fieldName)}
												fetchNextPage={onLoadNextPage}
												hasNextPage={hasNextPage}
												isFetchingNextPage={isFetchingNextPage}
												isNextPageError={isNextPageError}
												scrollContainerRef={scrollContainerRef}
											/>
										)}
									</div>
									<footer className="flex w-full items-center justify-end border-t border-border bg-background px-4 py-3">
										{hasEmptyNewVariable ? (
											<Tooltip>
												<TooltipTrigger asChild>
													<Button
														type="button"
														variant="ghost"
														size="icon-sm"
														className="mr-0.5"
														aria-label={t('tasklist.variablesFillAllFieldsWarning')}
													>
														<CircleAlert aria-hidden />
													</Button>
												</TooltipTrigger>
												<TooltipContent side="top">{t('tasklist.variablesFillAllFieldsWarning')}</TooltipContent>
											</Tooltip>
										) : null}
										<CompleteTaskButton
											status={completionStatus}
											isHidden={isCompletionHidden}
											isDisabled={
												submitting || hasValidationErrors || validating || hasEmptyNewVariable || !isCompletionAllowed
											}
										/>
									</footer>
								</div>
								<Suspense>
									<JSONEditorModal
										key={`${editingVariable ?? 'closed'}-${
											isJsonEditorModalOpen ? ((get(values, editingVariable) as string | undefined) ?? '') : ''
										}`}
										isOpen={isJsonEditorModalOpen}
										value={isJsonEditorModalOpen ? ((get(values, editingVariable) as string | undefined) ?? '') : ''}
										isReadOnly={!isEditingAllowed || isCompletionBusy}
										onClose={() => setEditingVariable(undefined)}
										onSave={(value) => {
											if (editingVariable !== undefined) {
												form.change(editingVariable, value);
												setEditingVariable(undefined);
											}
										}}
									/>
								</Suspense>
							</form>
						</div>
					</div>
				);
			}}
		</Form>
	);
};

export {TaskDetailsVariables};
