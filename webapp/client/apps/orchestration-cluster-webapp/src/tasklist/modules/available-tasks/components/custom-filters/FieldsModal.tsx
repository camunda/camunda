/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment, Suspense} from 'react';
import {
	Button,
	ComposedModal,
	DatePicker,
	DatePickerInput,
	FormGroup,
	ModalBody,
	ModalFooter,
	ModalHeader,
	RadioButton,
	RadioButtonGroup,
	Select,
	SelectItem,
	SelectSkeleton,
	TextInput,
	Toggle,
} from '@carbon/react';
import {Close, Add} from '@carbon/react/icons';
import {ErrorBoundary} from 'react-error-boundary';
import {Field, Form} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import arrayMutators from 'final-form-arrays';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {queries} from '#/shared/http/queries';
import {cn} from '#/shared/cn';
import {
	type NamedCustomFilters,
	namedCustomFiltersSchema,
} from '#/tasklist/modules/available-tasks/customFiltersSchema';
import {ProcessesSelect} from './ProcessesSelect';
import {ProcessesSelectErrorFallback} from './ProcessesSelectErrorFallback';
import {MultitenancySelect} from './MultitenancySelect';
import styles from './FieldsModal.module.scss';
import {getClientConfig} from '#/shared/config/getClientConfig';

type FormValues = NamedCustomFilters & {
	areAdvancedFiltersEnabled: boolean;
	action: 'apply' | 'save' | 'edit';
};

const DEFAULT_FORM_VALUES: NamedCustomFilters = {
	assignee: 'all',
	status: 'all',
};

const ADVANCED_FILTERS: Array<keyof NamedCustomFilters> = [
	'dueDateFrom',
	'dueDateTo',
	'followUpDateFrom',
	'followUpDateTo',
	'taskId',
	'variables',
];

function getDateValue(date: Date[]): Date | undefined {
	if (date.length !== 1) {
		return undefined;
	}

	return date[0];
}

function setPath<T extends Record<string, unknown>>(obj: T, key: PropertyKey, value: unknown): T {
	return {...obj, [String(key)]: value};
}

type Props = {
	isOpen: boolean;
	onClose: () => void;
	onApply: (values: NamedCustomFilters) => void;
	onSave: (values: NamedCustomFilters) => void;
	onEdit: (values: NamedCustomFilters) => void;
	onDelete: (filterName: string) => void;
	initialValues: NamedCustomFilters;
};

const FieldsModal: React.FC<Props> = ({isOpen, onClose, onApply, onSave, onEdit, onDelete, initialValues}) => {
	const {t, i18n} = useTranslation();
	const label = t('tasklist.customFiltersModalAdvancedFiltersLabel');
	const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
	const groups = currentUser?.groups ?? [];

	return (
		<ComposedModal
			open={isOpen}
			preventCloseOnClickOutside
			size="md"
			onClose={onClose}
			aria-label={t('tasklist.customFiltersModalAriaLabel')}
		>
			{isOpen ? (
				<Form<FormValues>
					onSubmit={({areAdvancedFiltersEnabled: _, action, ...values}) => {
						const result = namedCustomFiltersSchema.safeParse(values);

						if (!result.success) {
							return result.error.flatten(({path, message}) => {
								const [firstPath] = path;

								if (firstPath !== 'variables') {
									return message;
								}

								const lastPathSegment = path.at(-1);

								return lastPathSegment === undefined ? message : setPath({}, lastPathSegment, message);
							}).fieldErrors;
						}

						if (action === 'apply') {
							onApply(result.data);
							return;
						}

						if (action === 'save') {
							onSave(result.data);
							return;
						}

						if (action === 'edit') {
							onEdit(result.data);
						}

						return;
					}}
					initialValues={{
						...initialValues,
						areAdvancedFiltersEnabled: ADVANCED_FILTERS.some((key) => initialValues?.[key] !== undefined),
					}}
					mutators={{...arrayMutators}}
				>
					{({handleSubmit, form, values}) => (
						<>
							<ModalHeader
								title={t('tasklist.customFiltersModalApplyFiltersTitle')}
								iconDescription={t('tasklist.optionsModalCloseButton')}
								buttonOnClick={onClose}
							/>
							<ModalBody hasForm>
								<form className={styles.twoColumnGrid} onSubmit={handleSubmit} tabIndex={-1}>
									{[undefined, 'custom'].includes(initialValues?.name) ? null : (
										<Field name="name" defaultValue="">
											{({input}) => (
												<TextInput
													{...input}
													id={input.name}
													labelText={t('tasklist.customFiltersModalFilterNameLabel')}
													className={styles.nameField}
												/>
											)}
										</Field>
									)}

									<Field name="assignee">
										{({input}) => (
											<RadioButtonGroup
												legendText={t('tasklist.customFiltersModalAssigneeLabel')}
												name={input.name}
												onChange={input.onChange}
												valueSelected={input.value}
												defaultSelected="all"
												orientation="vertical"
											>
												<RadioButton
													labelText={t('tasklist.customFiltersModalAssigneeAll')}
													value="all"
													data-modal-primary-focus
												/>
												<RadioButton
													labelText={t('tasklist.customFiltersModalAssigneeUnassigned')}
													value="unassigned"
												/>
												<RadioButton labelText={t('tasklist.customFiltersModalAssigneeMe')} value="me" />
												<RadioButton
													labelText={t('tasklist.customFiltersModalAssigneeUserAndGround')}
													value="user-and-group"
												/>
											</RadioButtonGroup>
										)}
									</Field>

									<Field name="status">
										{({input}) => (
											<RadioButtonGroup
												legendText={t('tasklist.customFiltersModalStatusLabel')}
												name={input.name}
												onChange={input.onChange}
												valueSelected={input.value}
												defaultSelected="all"
												orientation="vertical"
												className={styles.secondColumn}
											>
												<RadioButton labelText={t('tasklist.customFiltersModalStatusAll')} value="all" />
												<RadioButton labelText={t('tasklist.customFiltersModalStatusOpen')} value="open" />
												<RadioButton labelText={t('tasklist.customFiltersModalStatusCompleted')} value="completed" />
											</RadioButtonGroup>
										)}
									</Field>

									{values?.assignee === 'user-and-group' ? (
										<>
											<Field name="assignedTo">
												{({input}) => (
													<TextInput
														{...input}
														id={input.name}
														labelText={t('tasklist.customFiltersModalAssignedToLabel')}
														placeholder={t('tasklist.customFiltersModalAssignedToPlaceholder')}
													/>
												)}
											</Field>
											<Field name="candidateGroup">
												{({input}) =>
													groups.length === 0 ? (
														<TextInput
															{...input}
															id={input.name}
															labelText={t('tasklist.customFiltersModalInAGroupLabel')}
															className={styles.secondColumn}
															placeholder={t('tasklist.customFiltersModalInAGroupPlaceholder')}
															disabled={currentUser === undefined}
														/>
													) : (
														<Select
															{...input}
															id={input.name}
															labelText={t('tasklist.customFiltersModalInAGroupLabel')}
															className={styles.secondColumn}
														>
															<SelectItem value="" text="" />
															{groups.map((group) => (
																<SelectItem key={group} value={group} text={group} />
															))}
														</Select>
													)
												}
											</Field>
										</>
									) : null}
									<Field name="bpmnProcess">
										{({input}) => (
											<ErrorBoundary FallbackComponent={ProcessesSelectErrorFallback}>
												<Suspense fallback={<SelectSkeleton />}>
													<ProcessesSelect
														{...input}
														id={input.name}
														tenantId={values?.tenant}
														labelText={t('tasklist.customFiltersModalLatestProcessVersionLabel')}
													/>
												</Suspense>
											</ErrorBoundary>
										)}
									</Field>
									{getClientConfig().deployment.isMultiTenancyEnabled ? (
										<Field name="tenant">
											{({input}) => (
												<MultitenancySelect
													{...input}
													id={input.name}
													labelText={t('tasklist.multiTenancyDropdownLabel')}
													className={styles.secondColumn}
												/>
											)}
										</Field>
									) : null}

									<Field name="areAdvancedFiltersEnabled">
										{({input}) => (
											<Toggle
												id="toggle-advanced-filters"
												className={styles.toggle}
												size="sm"
												labelText={label}
												aria-label={label}
												hideLabel
												labelA={t('tasklist.customFiltersModalAdvancedFiltersToggleHidden')}
												labelB={t('tasklist.customFiltersModalAdvancedFiltersToggleVisible')}
												toggled={input.value}
												onToggle={input.onChange}
											/>
										)}
									</Field>

									{values?.areAdvancedFiltersEnabled ? (
										<>
											<FormGroup
												className={styles.dateRangeFormGroup}
												legendText={t('tasklist.customFiltersModalDueDateLabel')}
											>
												<Field name="dueDateFrom">
													{({input}) => (
														<DatePicker
															{...input}
															onChange={(dates) => {
																input.onChange(getDateValue(dates));
															}}
															className={styles.datePicker}
															datePickerType="single"
															dateFormat={t('globalWrittenCalendarDateFormat')}
															locale={{locale: i18n.resolvedLanguage}}
														>
															<DatePickerInput
																id="due-date-from"
																placeholder={t('globalDatePlaceholder')}
																labelText={t('tasklist.customFiltersModalFromLabel')}
																size="md"
															/>
														</DatePicker>
													)}
												</Field>
												<Field name="dueDateTo">
													{({input}) => (
														<DatePicker
															{...input}
															onChange={(dates) => {
																input.onChange(getDateValue(dates));
															}}
															className={styles.datePicker}
															datePickerType="single"
															dateFormat={t('globalWrittenCalendarDateFormat')}
															locale={{locale: i18n.resolvedLanguage}}
														>
															<DatePickerInput
																id="due-date-to"
																placeholder={t('globalDatePlaceholder')}
																labelText={t('tasklist.customFiltersModalToLabel')}
																size="md"
															/>
														</DatePicker>
													)}
												</Field>
											</FormGroup>

											<FormGroup
												legendText={t('tasklist.customFiltersModalFollowUpDateLabel')}
												className={cn(styles.dateRangeFormGroup, styles.secondColumn)}
											>
												<Field name="followUpDateFrom">
													{({input}) => (
														<DatePicker
															{...input}
															onChange={(dates) => {
																input.onChange(getDateValue(dates));
															}}
															className={styles.datePicker}
															datePickerType="single"
															dateFormat={t('globalWrittenCalendarDateFormat')}
															locale={{locale: i18n.resolvedLanguage}}
														>
															<DatePickerInput
																id="follow-up-date-from"
																placeholder={t('globalDatePlaceholder')}
																labelText={t('tasklist.customFiltersModalFromLabel')}
																size="md"
															/>
														</DatePicker>
													)}
												</Field>
												<Field name="followUpDateTo">
													{({input}) => (
														<DatePicker
															{...input}
															onChange={(dates) => {
																input.onChange(getDateValue(dates));
															}}
															className={styles.datePicker}
															datePickerType="single"
															dateFormat={t('globalWrittenCalendarDateFormat')}
															locale={{locale: i18n.resolvedLanguage}}
														>
															<DatePickerInput
																id="follow-up-date-to"
																placeholder={t('globalDatePlaceholder')}
																labelText={t('tasklist.customFiltersModalToLabel')}
																size="md"
															/>
														</DatePicker>
													)}
												</Field>
											</FormGroup>

											<Field name="taskId">
												{({input}) => (
													<TextInput
														{...input}
														id={input.name}
														labelText={t('tasklist.customFiltersModalTaskIDLabel')}
													/>
												)}
											</Field>

											<FieldArray name="variables">
												{({fields, meta: arrayMeta}) => (
													<FormGroup
														className={styles.variableFormGroup}
														legendText={t('tasklist.customFiltersModalTaskVariableLabel')}
													>
														<div className={styles.variableGrid}>
															{fields.map((name, index) => (
																<Fragment key={name}>
																	<Field name={`${name}.name`}>
																		{({input, meta}) => (
																			<TextInput
																				{...input}
																				id={input.name}
																				className={styles.variableGridItem}
																				labelText={t('tasklist.taskDetailsNewVariableNameFieldLabel')}
																				autoFocus={index === (fields.length ?? 1) - 1}
																				invalid={meta.submitError !== undefined && !arrayMeta.dirtySinceLastSubmit}
																				invalidText={meta.submitError}
																			/>
																		)}
																	</Field>

																	<Field name={`${name}.value`}>
																		{({input, meta}) => (
																			<TextInput
																				{...input}
																				id={input.name}
																				className={styles.variableGridItem}
																				labelText={t('tasklist.customFiltersModalVariableValueLabel')}
																				invalid={meta.submitError !== undefined && !arrayMeta.dirtySinceLastSubmit}
																				invalidText={meta.submitError}
																			/>
																		)}
																	</Field>

																	<Button
																		type="button"
																		className={styles.variableGridRemove}
																		hasIconOnly
																		iconDescription={t('tasklist.customFiltersModalRemoveVariableButton')}
																		renderIcon={Close}
																		kind="ghost"
																		size="md"
																		onClick={() => fields.remove(index)}
																		tooltipPosition="left"
																	/>
																</Fragment>
															))}
														</div>

														<Button
															type="button"
															iconDescription={t('tasklist.customFiltersModalAddVariableButtonAria')}
															renderIcon={Add}
															kind="tertiary"
															size="md"
															onClick={() => fields.push({name: '', value: ''})}
														>
															{t('tasklist.customFiltersModalAddVariableButton')}
														</Button>
													</FormGroup>
												)}
											</FieldArray>
										</>
									) : null}
								</form>
							</ModalBody>
							<ModalFooter className={styles.modalFooter}>
								<Button
									kind="ghost"
									onClick={() => {
										form.reset(
											values?.name === undefined
												? DEFAULT_FORM_VALUES
												: {
														...DEFAULT_FORM_VALUES,
														name: values.name,
													},
										);
									}}
									type="button"
								>
									{t('tasklist.customFiltersModalResetButton')}
								</Button>
								{initialValues?.name === undefined ? (
									<>
										<Button kind="secondary" onClick={onClose} type="button">
											{t('tasklist.customFiltersModalCancelButton')}
										</Button>
										<Button
											kind="secondary"
											onClick={() => {
												form.change('action', 'save');
												form.submit();
											}}
											type="submit"
										>
											{t('tasklist.customFiltersModalSaveButton')}
										</Button>
										<Button
											kind="primary"
											type="submit"
											onClick={() => {
												form.change('action', 'apply');
												form.submit();
											}}
										>
											{t('tasklist.customFiltersModalApplyButton')}
										</Button>
									</>
								) : (
									<>
										<Button kind="secondary" onClick={() => onDelete(values?.name ?? '')} type="button">
											{t('tasklist.customFiltersModalDeleteButton')}
										</Button>
										<Button kind="secondary" onClick={onClose} type="button">
											{t('tasklist.customFiltersModalCancelButton')}
										</Button>
										<Button
											kind="primary"
											onClick={() => {
												form.change('action', 'edit');
												form.submit();
											}}
											type="submit"
										>
											{t('tasklist.customFiltersModalSaveAndApplyButton')}
										</Button>
									</>
								)}
							</ModalFooter>
						</>
					)}
				</Form>
			) : null}
		</ComposedModal>
	);
};

export {FieldsModal};
