/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense, useCallback, useMemo, useRef, useState} from 'react';
import {Button, Heading, IconButton, Layer} from '@carbon/react';
import {Add, Information} from '@carbon/react/icons';
import {C3EmptyState} from '@camunda/camunda-composite-components';
import {Form} from 'react-final-form';
import arrayMutators from 'final-form-arrays';
import {useQueryClient, type InfiniteData} from '@tanstack/react-query';
import type {QueryVariablesByUserTaskResponseBody, Variable} from '@camunda/camunda-api-zod-schemas/8.10';
import get from 'lodash/get';
import intersection from 'lodash/intersection';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
import {queries} from '#/shared/http/queries';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {CompleteTaskButton} from '#/tasklist/modules/task-details/components/CompleteTaskButton';
import type {CompletionStatus} from '#/tasklist/modules/task-details/useTaskCompletion';
import {VariableEditor} from '#/tasklist/modules/task-details-variables/components/VariableEditor';
import {ResetForm} from '#/tasklist/modules/task-details-variables/components/ResetForm';
import type {VariablesFormValues} from '#/tasklist/modules/task-details-variables/types';
import {
	createVariableFieldName,
	getVariableFieldName,
} from '#/tasklist/modules/task-details-variables/variableFieldNames';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';
import styles from './TaskDetailsVariables.module.scss';

const JSONEditorModal = lazy(async () => {
	const [{loadMonaco}, {JSONEditorModal}] = await Promise.all([
		import('#/tasklist/modules/task-details-variables/loadMonaco'),
		import('#/tasklist/modules/task-details-variables/components/JSONEditorModal'),
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
				const variable = await queryClient.fetchQuery(queries.getVariable(variableKey));
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
				notificationsStore.displayNotification({
					kind: 'error',
					title: t('tasklist.variablesFullValueFetchError'),
					isDismissable: true,
				});
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

				return (
					<>
						<div className={styles.header}>
							<Heading>{t('tasklist.variablesTitle')}</Heading>
							{isCompleted ? null : (
								<Button
									kind="ghost"
									type="button"
									size="sm"
									onClick={() => form.mutators.push?.('newVariables')}
									renderIcon={Add}
									disabled={!isEditingAllowed || isCompletionBusy}
									title={isEditingAllowed ? undefined : t('tasklist.variablesAddVariableTooltip')}
								>
									{t('tasklist.taskVariablesAddVariable')}
								</Button>
							)}
						</div>
						<hr className={styles.separator} />
						<div
							className={styles.scrollContainer}
							ref={scrollContainerRef}
							data-testid="variables-scroll-container"
							onScroll={(event) => {
								const {scrollHeight, scrollTop, clientHeight} = event.currentTarget;
								if (Math.floor(scrollHeight - clientHeight - scrollTop) <= 0) {
									onLoadNextPage();
								}
							}}
						>
							<form className={styles.form} onSubmit={handleSubmit} data-testid="variables-table" ref={formRef}>
								<ResetForm isAssigned={isEditingAllowed} />
								<div className={styles.content} tabIndex={-1} data-testid="task-tab-content">
									{variables.length === 0 && (values.newVariables?.length ?? 0) === 0 ? (
										<Layer className={cn(styles.layerContainer, styles.gutter)}>
											<C3EmptyState
												heading={t('tasklist.variablesNoVariablesHeading')}
												description={isCompleted ? '' : t('tasklist.variablesClickOnAddVariablesPrompt')}
											/>
										</Layer>
									) : (
										<Layer className={styles.layerContainer} data-testid="variables-form-table">
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
										</Layer>
									)}
									<div className={styles.footer}>
										{hasEmptyNewVariable ? (
											<IconButton
												className={styles.warning}
												kind="ghost"
												label={t('tasklist.variablesFillAllFieldsWarning')}
												align="top"
											>
												<Information size={20} />
											</IconButton>
										) : null}
										<CompleteTaskButton
											status={completionStatus}
											isHidden={isCompletionHidden}
											isDisabled={
												submitting || hasValidationErrors || validating || hasEmptyNewVariable || !isCompletionAllowed
											}
										/>
									</div>
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
					</>
				);
			}}
		</Form>
	);
};

export {TaskDetailsVariables};
