/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// DS-only variables table (see VariableEditor.tsx for the flag dispatch).
//
// A full rewrite rather than an import swap: carbon-compat's StructuredList is a
// bare passthrough re-export of @carbon/react ("MIGRATION TODO: no shadcn
// equivalent yet"), so swapping the imports would leave this table visually
// Carbon with the flag on. Same class of gap as carbon-compat's
// actionable-notification, and logged for the DS team alongside it.
//
// Behaviour is carried over unchanged from VariableEditor.tsx: the read-only
// branch stays virtualized, and every react-final-form binding (Field,
// DelayedErrorField, the validators, and the dirtyFields-driven error delay)
// keeps its exact field names — those come from createVariableFieldName /
// createNewVariableFieldName and the form state is keyed on them.
import {type RefObject, useEffect} from 'react';
import {Button, Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@camunda/design-system';
import {CloseIcon, InlineNotification, MaximizeIcon, SkeletonText} from '#/shared/design-system-compat';
import {useVirtualizer} from '@tanstack/react-virtual';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.10';
import {Field, useFormState} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import {useTranslation} from 'react-i18next';
import type {VariablesFormValues} from '#/tasklist/modules/task-details-variables/types';
import {
	createNewVariableFieldName,
	createVariableFieldName,
} from '#/tasklist/modules/task-details-variables/variableFieldNames';
import {mergeValidators} from '#/tasklist/modules/task-details-variables/mergeValidators';
import {
	validateDuplicateNames,
	validateNameCharacters,
	validateNameComplete,
	validateValueComplete,
	validateValueJSON,
} from '#/tasklist/modules/task-details-variables/validators';
import {DelayedErrorField} from './DelayedErrorField';
import {LoadingTextarea} from './LoadingTextarea';
import {TextInput} from './TextInput';
import {OnNewVariableAdded} from './OnNewVariableAdded';
import styles from './VariableEditor.module.scss';

// Must stay in step with the row height the DS table actually renders, or the
// virtualizer's scroll math drifts. Same value the Carbon table used.
const ESTIMATED_ROW_HEIGHT = 48;
const OVERSCAN = 5;
const COLUMN_COUNT = 3;

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

const VariableEditorDS: React.FC<Props> = ({
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
		<Table size="md" className={styles.list}>
			<TableHeader>
				<TableRow>
					<TableHead className={styles.nameCell}>{t('tasklist.variableEditorVariableNameHeader')}</TableHead>
					<TableHead className={styles.valueCell}>{t('tasklist.variableEditorVariableValueHeader')}</TableHead>
					<TableHead className={styles.controlsCell}>
						<span className="sr-only">{t('tasklist.variableEditorOpenJsonLabel')}</span>
					</TableHead>
				</TableRow>
			</TableHeader>
			<TableBody>
				{readOnly ? (
					<>
						{/* Virtualizer spacers. Carbon's StructuredList tolerated bare <div>s here;
						    a real <tbody> does not, so they are empty rows with a colSpan cell. */}
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
										<TableCell className={styles.nameCell}>
											<SkeletonText />
										</TableCell>
										<TableCell className={styles.valueCell}>
											<SkeletonText />
										</TableCell>
										<TableCell className={styles.controlsCell} />
									</TableRow>
								);
							}

							return (
								<TableRow key={variable.variableKey}>
									<TableCell className={styles.nameCell}>{variable.name}</TableCell>
									<TableCell className={styles.valueCell}>
										<div className={styles.singleLineValue}>{variable.value}</div>
									</TableCell>
									<TableCell className={styles.controlsCell}>
										<div className={styles.controls}>
											<Button
												variant="ghost"
												size="icon-sm"
												aria-label={t('tasklist.variableEditorOpenJsonLabel')}
												title={t('tasklist.variableEditorOpenJsonLabel')}
												onClick={() => {
													if (variable.isTruncated) {
														void fetchFullVariable(variable.variableKey);
													}
													onMaximizeClick(createVariableFieldName(variable.name), variable.value);
												}}
											>
												<MaximizeIcon aria-hidden />
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
									<TableCell className={styles.nameCell}>{variable.name}</TableCell>
									<TableCell className={styles.valueCell}>
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
															void fetchFullVariable(variable.variableKey);
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
									<TableCell className={styles.controlsCell}>
										<div className={styles.controls}>
											<Button
												variant="ghost"
												size="icon-sm"
												aria-label={t('tasklist.variableEditorOpenJsonLabel')}
												title={t('tasklist.variableEditorOpenJsonLabel')}
												disabled={isDisabled}
												onClick={() => {
													if (variable.isTruncated) {
														void fetchFullVariable(variable.variableKey);
													}
													onMaximizeClick(fieldName, variable.value);
												}}
											>
												<MaximizeIcon aria-hidden />
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
											<TableCell className={styles.nameCell}>
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
											<TableCell className={styles.valueCell}>
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
											<TableCell className={styles.controlsCell}>
												<div className={styles.controls}>
													<Button
														variant="ghost"
														size="icon-sm"
														aria-label={t('tasklist.variableEditorOpenJsonLabel')}
														title={t('tasklist.variableEditorOpenJsonLabel')}
														disabled={isDisabled}
														onClick={() => {
															onMaximizeClick(valueFieldName, '');
														}}
													>
														<MaximizeIcon aria-hidden />
													</Button>
													<Button
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
														onClick={() => {
															fields.remove(index);
														}}
													>
														<CloseIcon aria-hidden />
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
							<div className={styles.paginationError}>
								<InlineNotification
									kind="error"
									hideCloseButton
									role="alert"
									lowContrast
									title={t('tasklist.taskDetailsFailedToFetchVariablesErrorTitle')}
									subtitle={t('tasklist.taskDetailsFailedToFetchVariablesErrorSubtitle')}
								/>
								{/* Carbon `kind="tertiary"` maps to the DS secondary button. */}
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

export {VariableEditorDS};
