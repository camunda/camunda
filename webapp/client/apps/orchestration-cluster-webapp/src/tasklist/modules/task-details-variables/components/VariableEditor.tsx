/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type RefObject, useEffect} from 'react';
import {
	Button,
	IconButton,
	InlineNotification,
	SkeletonText,
	StructuredListBody,
	StructuredListCell,
	StructuredListHead,
	StructuredListRow,
	StructuredListWrapper,
} from '@carbon/react';
import {Close, Maximize} from '@carbon/react/icons';
import {useVirtualizer} from '@tanstack/react-virtual';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.10';
import {Field, useFormState} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import {useTranslation} from 'react-i18next';
import {cn} from '#/shared/cn';
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

const ESTIMATED_ROW_HEIGHT = 48;
const OVERSCAN = 5;

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
		<StructuredListWrapper className={styles.list} isCondensed>
			<StructuredListHead>
				<StructuredListRow head>
					<StructuredListCell className={styles.cell} head>
						{t('tasklist.variableEditorVariableNameHeader')}
					</StructuredListCell>
					<StructuredListCell className={styles.cell} head>
						{t('tasklist.variableEditorVariableValueHeader')}
					</StructuredListCell>
					<StructuredListCell className={styles.cell} head>
						<span className="cds--visually-hidden">{t('tasklist.variableEditorOpenJsonLabel')}</span>
					</StructuredListCell>
				</StructuredListRow>
			</StructuredListHead>
			<StructuredListBody>
				{readOnly ? (
					<>
						{paddingTop > 0 ? <div style={{height: paddingTop}} aria-hidden /> : null}
						{virtualItems.map((virtualRow) => {
							const variable = variables[virtualRow.index];
							if (variable === undefined) {
								return (
									<StructuredListRow key={virtualRow.index}>
										<StructuredListCell className={cn(styles.cell, styles.nameCell)}>
											<SkeletonText />
										</StructuredListCell>
										<StructuredListCell className={cn(styles.cell, styles.valueCell)}>
											<SkeletonText />
										</StructuredListCell>
										<StructuredListCell className={cn(styles.cell, styles.controlsCell)} />
									</StructuredListRow>
								);
							}

							return (
								<StructuredListRow key={variable.variableKey}>
									<StructuredListCell className={cn(styles.cell, styles.nameCell)}>{variable.name}</StructuredListCell>
									<StructuredListCell className={cn(styles.cell, styles.valueCell)}>
										<div className={styles.singleLineValue}>{variable.value}</div>
									</StructuredListCell>
									<StructuredListCell className={cn(styles.cell, styles.controlsCell)}>
										<div className={styles.controls}>
											<IconButton
												label={t('tasklist.variableEditorOpenJsonLabel')}
												onClick={() => {
													if (variable.isTruncated) {
														void fetchFullVariable(variable.variableKey);
													}
													onMaximizeClick(createVariableFieldName(variable.name), variable.value);
												}}
												size="sm"
												kind="ghost"
												align="top-end"
												leaveDelayMs={100}
											>
												<Maximize />
											</IconButton>
										</div>
									</StructuredListCell>
								</StructuredListRow>
							);
						})}
						{paddingBottom > 0 ? <div style={{height: paddingBottom}} aria-hidden /> : null}
					</>
				) : (
					<>
						{variables.map((variable) => {
							const fieldName = createVariableFieldName(variable.name);
							return (
								<StructuredListRow key={variable.name}>
									<StructuredListCell className={cn(styles.cell, styles.nameCell)}>{variable.name}</StructuredListCell>
									<StructuredListCell className={cn(styles.cell, styles.valueCell)}>
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
									</StructuredListCell>
									<StructuredListCell className={cn(styles.cell, styles.controlsCell)}>
										<div className={styles.controls}>
											<IconButton
												label={t('tasklist.variableEditorOpenJsonLabel')}
												onClick={() => {
													if (variable.isTruncated) {
														void fetchFullVariable(variable.variableKey);
													}
													onMaximizeClick(fieldName, variable.value);
												}}
												disabled={isDisabled}
												size="sm"
												kind="ghost"
												align="top-end"
												leaveDelayMs={100}
											>
												<Maximize />
											</IconButton>
										</div>
									</StructuredListCell>
								</StructuredListRow>
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
										<StructuredListRow key={variable}>
											<StructuredListCell className={cn(styles.cell, styles.nameCell)}>
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
											</StructuredListCell>
											<StructuredListCell className={cn(styles.cell, styles.valueCell)}>
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
											</StructuredListCell>
											<StructuredListCell className={cn(styles.cell, styles.controlsCell)}>
												<div className={styles.controls}>
													<IconButton
														label={t('tasklist.variableEditorOpenJsonLabel')}
														onClick={() => onMaximizeClick(valueFieldName, '')}
														disabled={isDisabled}
														size="sm"
														kind="ghost"
														align="top-end"
														leaveDelayMs={100}
													>
														<Maximize />
													</IconButton>
													<IconButton
														label={t('tasklist.taskVariablesRemoveVariable', {count: index + 1, ordinal: true})}
														onClick={() => fields.remove(index)}
														disabled={isDisabled}
														size="sm"
														kind="ghost"
														align="top-end"
														leaveDelayMs={100}
													>
														<Close />
													</IconButton>
												</div>
											</StructuredListCell>
										</StructuredListRow>
									);
								})
							}
						</FieldArray>
					</>
				)}
				{isNextPageError ? (
					<StructuredListRow>
						<StructuredListCell>
							<div className={styles.paginationError}>
								<InlineNotification
									kind="error"
									hideCloseButton
									role="alert"
									lowContrast
									title={t('tasklist.taskDetailsFailedToFetchVariablesErrorTitle')}
									subtitle={t('tasklist.taskDetailsFailedToFetchVariablesErrorSubtitle')}
								/>
								<Button kind="tertiary" size="sm" type="button" onClick={fetchNextPage}>
									{t('tasklist.taskDetailsProcessRetryButtonLabel')}
								</Button>
							</div>
						</StructuredListCell>
						<StructuredListCell />
						<StructuredListCell />
					</StructuredListRow>
				) : null}
			</StructuredListBody>
		</StructuredListWrapper>
	);
};

export {VariableEditor};
