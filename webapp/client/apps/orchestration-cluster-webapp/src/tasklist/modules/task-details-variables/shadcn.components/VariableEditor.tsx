/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	Alert,
	Button,
	Skeleton,
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from '@camunda/design-system';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.10';
import {useVirtualizer} from '@tanstack/react-virtual';
import {Maximize2, X} from 'lucide-react';
import {type RefObject, useEffect} from 'react';
import {Field, useFormState} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import {useTranslation} from 'react-i18next';
import {mergeValidators} from '#/tasklist/modules/task-details-variables/mergeValidators';
import type {VariablesFormValues} from '#/tasklist/modules/task-details-variables/types';
import {
	validateDuplicateNames,
	validateNameCharacters,
	validateNameComplete,
	validateValueComplete,
	validateValueJSON,
} from '#/tasklist/modules/task-details-variables/validators';
import {
	createNewVariableFieldName,
	createVariableFieldName,
} from '#/tasklist/modules/task-details-variables/variableFieldNames';
import {DelayedErrorField} from './DelayedErrorField';
import {LoadingTextarea} from './LoadingTextarea';
import {OnNewVariableAdded} from './OnNewVariableAdded';
import {TextInput} from './TextInput';

const ESTIMATED_ROW_HEIGHT = 48;
const OVERSCAN = 5;
const COLUMN_COUNT = 3;
const NAME_CELL_CLASS = 'w-50 align-middle';
const VALUE_CELL_CLASS = 'min-w-0 break-all align-middle';
const CONTROLS_CELL_CLASS = 'w-20 align-middle';

type Props = {
	containerRef: RefObject<HTMLElement | null>;
	variables: Variable[];
	totalVariables: number;
	readOnly: boolean;
	isDisabled: boolean;
	fetchFullVariable: (variableKey: string) => Promise<void>;
	variablesLoadingFullValue: string[];
	onMaximizeClick: (fieldName: string, value: string) => void;
	fetchNextPage: () => void;
	hasNextPage: boolean;
	isFetchingNextPage: boolean;
	isNextPageError: boolean;
	scrollContainerRef: RefObject<HTMLElement | null>;
};

const VariableEditor: React.FC<Props> = ({
	containerRef,
	variables,
	totalVariables,
	readOnly,
	isDisabled,
	fetchFullVariable,
	variablesLoadingFullValue,
	onMaximizeClick,
	fetchNextPage,
	hasNextPage,
	isFetchingNextPage,
	isNextPageError,
	scrollContainerRef,
}) => {
	const {dirtyFields} = useFormState<VariablesFormValues>();
	const {t} = useTranslation();
	// eslint-disable-next-line react-hooks/incompatible-library
	const virtualizer = useVirtualizer({
		count: totalVariables,
		getScrollElement: () => scrollContainerRef.current,
		estimateSize: () => ESTIMATED_ROW_HEIGHT,
		overscan: OVERSCAN,
		enabled: readOnly,
	});
	const virtualItems = virtualizer.getVirtualItems();

	useEffect(() => {
		if (!readOnly) {
			return;
		}

		const lastItem = virtualItems.at(-1);
		if (
			lastItem !== undefined &&
			lastItem.index >= variables.length - 1 &&
			hasNextPage &&
			!isFetchingNextPage &&
			!isNextPageError
		) {
			fetchNextPage();
		}
	}, [fetchNextPage, hasNextPage, isFetchingNextPage, isNextPageError, readOnly, variables.length, virtualItems]);

	const paddingTop = virtualItems[0]?.start ?? 0;
	const paddingBottom = isNextPageError ? 0 : virtualizer.getTotalSize() - (virtualItems.at(-1)?.end ?? 0);

	return (
		<Table size="md" className="h-min table-fixed">
			<TableHeader>
				<TableRow>
					<TableHead className={NAME_CELL_CLASS}>{t('tasklist.variableEditorVariableNameHeader')}</TableHead>
					<TableHead className={VALUE_CELL_CLASS}>{t('tasklist.variableEditorVariableValueHeader')}</TableHead>
					<TableHead className={CONTROLS_CELL_CLASS}>
						<span className="sr-only">{t('tasklist.variableEditorOpenJsonLabel')}</span>
					</TableHead>
				</TableRow>
			</TableHeader>
			<TableBody>
				{readOnly ? (
					<>
						{paddingTop > 0 ? (
							<TableRow aria-hidden>
								<TableCell colSpan={COLUMN_COUNT} style={{height: paddingTop, padding: 0}} />
							</TableRow>
						) : null}
						{virtualItems.map((virtualRow) => {
							const variable = variables[virtualRow.index];
							if (variable === undefined) {
								return (
									<TableRow key={virtualRow.index}>
										<TableCell className={NAME_CELL_CLASS}>
											<Skeleton className="h-4 w-full" />
										</TableCell>
										<TableCell className={VALUE_CELL_CLASS}>
											<Skeleton className="h-4 w-full" />
										</TableCell>
										<TableCell className={CONTROLS_CELL_CLASS} />
									</TableRow>
								);
							}

							return (
								<TableRow key={variable.variableKey}>
									<TableCell className={NAME_CELL_CLASS}>{variable.name}</TableCell>
									<TableCell className={VALUE_CELL_CLASS}>
										<div className="max-w-full overflow-hidden text-ellipsis whitespace-nowrap">{variable.value}</div>
									</TableCell>
									<TableCell className={CONTROLS_CELL_CLASS}>
										<div className="flex justify-end pr-2">
											<Button
												type="button"
												variant="ghost"
												size="icon-sm"
												aria-label={t('tasklist.variableEditorOpenJsonLabel')}
												title={t('tasklist.variableEditorOpenJsonLabel')}
												onClick={() => {
													if (variable.isTruncated) {
														fetchFullVariable(variable.variableKey);
													}
													onMaximizeClick(createVariableFieldName(variable.name), variable.value);
												}}
											>
												<Maximize2 aria-hidden />
											</Button>
										</div>
									</TableCell>
								</TableRow>
							);
						})}
						{paddingBottom > 0 ? (
							<TableRow aria-hidden>
								<TableCell colSpan={COLUMN_COUNT} style={{height: paddingBottom, padding: 0}} />
							</TableRow>
						) : null}
					</>
				) : (
					<>
						{variables.map((variable) => {
							const fieldName = createVariableFieldName(variable.name);
							return (
								<TableRow key={variable.name}>
									<TableCell className={NAME_CELL_CLASS}>{variable.name}</TableCell>
									<TableCell className={VALUE_CELL_CLASS}>
										<Field<string>
											name={fieldName}
											validate={variable.isTruncated ? () => undefined : validateValueJSON}
										>
											{({input, meta}) => (
												<LoadingTextarea
													{...input}
													id={input.name}
													invalidText={meta.error}
													isLoading={variablesLoadingFullValue.includes(variable.variableKey)}
													onFocus={(event) => {
														if (variable.isTruncated) {
															fetchFullVariable(variable.variableKey);
														}
														input.onFocus(event);
													}}
													isActive={meta.active}
													type="text"
													labelText={`${variable.name} ${t('tasklist.taskVariablesValueLabel')}`}
													placeholder={`${variable.name} ${t('tasklist.taskVariablesValueLabel')}`}
													disabled={isDisabled}
												/>
											)}
										</Field>
									</TableCell>
									<TableCell className={CONTROLS_CELL_CLASS}>
										<div className="flex justify-end pr-2">
											<Button
												type="button"
												variant="ghost"
												size="icon-sm"
												aria-label={t('tasklist.variableEditorOpenJsonLabel')}
												title={t('tasklist.variableEditorOpenJsonLabel')}
												disabled={isDisabled}
												onClick={() => {
													if (variable.isTruncated) {
														fetchFullVariable(variable.variableKey);
													}
													onMaximizeClick(fieldName, variable.value);
												}}
											>
												<Maximize2 aria-hidden />
											</Button>
										</div>
									</TableCell>
								</TableRow>
							);
						})}
						<OnNewVariableAdded
							name="newVariables"
							execute={() => {
								const element = containerRef.current?.parentElement;
								if (element !== null && element !== undefined) {
									element.scrollTop = element.scrollHeight;
								}
							}}
						/>
						<FieldArray name="newVariables">
							{({fields}) =>
								fields.map((variable, index) => {
									const nameFieldName = createNewVariableFieldName(variable, 'name');
									const valueFieldName = createNewVariableFieldName(variable, 'value');
									return (
										<TableRow key={variable}>
											<TableCell className={NAME_CELL_CLASS}>
												<DelayedErrorField
													name={nameFieldName}
													validate={mergeValidators(
														validateNameCharacters,
														validateNameComplete,
														validateDuplicateNames,
													)}
													addExtraDelay={Boolean(!dirtyFields?.[nameFieldName] && dirtyFields?.[valueFieldName])}
												>
													{({input, meta}) => (
														<TextInput
															{...input}
															id={input.name}
															invalidText={meta.error}
															labelText={t('tasklist.variableEditorVariableNameLabel', {
																count: index + 1,
																ordinal: true,
															})}
															placeholder={t('tasklist.variableEditorNamePlaceholder')}
															autoFocus
															disabled={isDisabled}
														/>
													)}
												</DelayedErrorField>
											</TableCell>
											<TableCell className={VALUE_CELL_CLASS}>
												<DelayedErrorField
													name={valueFieldName}
													validate={validateValueComplete}
													addExtraDelay={Boolean(dirtyFields?.[nameFieldName] && !dirtyFields?.[valueFieldName])}
												>
													{({input, meta}) => (
														<TextInput
															{...input}
															id={input.name}
															labelText={t('tasklist.variableEditorVariableValueLabel', {
																count: index + 1,
																ordinal: true,
															})}
															invalidText={meta.error}
															placeholder={t('tasklist.taskVariablesValueLabel')}
															disabled={isDisabled}
														/>
													)}
												</DelayedErrorField>
											</TableCell>
											<TableCell className={CONTROLS_CELL_CLASS}>
												<div className="flex justify-end pr-2">
													<Button
														type="button"
														variant="ghost"
														size="icon-sm"
														aria-label={t('tasklist.variableEditorOpenJsonLabel')}
														title={t('tasklist.variableEditorOpenJsonLabel')}
														disabled={isDisabled}
														onClick={() => onMaximizeClick(valueFieldName, '')}
													>
														<Maximize2 aria-hidden />
													</Button>
													<Button
														type="button"
														variant="ghost"
														size="icon-sm"
														aria-label={t('tasklist.taskVariablesRemoveVariable', {
															count: index + 1,
															ordinal: true,
														})}
														title={t('tasklist.taskVariablesRemoveVariable', {
															count: index + 1,
															ordinal: true,
														})}
														disabled={isDisabled}
														onClick={() => fields.remove(index)}
													>
														<X aria-hidden />
													</Button>
												</div>
											</TableCell>
										</TableRow>
									);
								})
							}
						</FieldArray>
					</>
				)}
				{isNextPageError ? (
					<TableRow>
						<TableCell colSpan={COLUMN_COUNT}>
							<div className="flex items-center gap-2">
								<Alert
									className="flex-1"
									variant="destructive"
									role="alert"
									title={t('tasklist.taskDetailsFailedToFetchVariablesErrorTitle')}
									description={t('tasklist.taskDetailsFailedToFetchVariablesErrorSubtitle')}
								/>
								<Button variant="secondary" size="sm" type="button" onClick={fetchNextPage}>
									{t('tasklist.taskDetailsProcessRetryButtonLabel')}
								</Button>
							</div>
						</TableCell>
					</TableRow>
				) : null}
			</TableBody>
		</Table>
	);
};

export {VariableEditor};
