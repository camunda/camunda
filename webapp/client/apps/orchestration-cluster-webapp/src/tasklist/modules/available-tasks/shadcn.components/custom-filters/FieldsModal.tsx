/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment, Suspense, useCallback} from 'react';
import {
	Button,
	DatePicker,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Input,
	Label,
	RadioGroup,
	RadioGroupItem,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
	Skeleton,
	Switch,
} from '@camunda/design-system';
import {Plus, X} from 'lucide-react';
import {ErrorBoundary} from 'react-error-boundary';
import {Field, Form, type FieldInputProps} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import arrayMutators from 'final-form-arrays';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {queries} from '#/shared/http/queries';
import {
	type NamedCustomFilters,
	namedCustomFiltersSchema,
} from '#/tasklist/modules/available-tasks/customFiltersSchema';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {ProcessesSelect} from './ProcessesSelect';
import {ProcessesSelectErrorFallback} from './ProcessesSelectErrorFallback';
import {MultitenancySelect} from './MultitenancySelect';
import {AdvancedStringFilter} from './AdvancedStringFilter';

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
	'businessId',
	'variables',
];

const EMPTY_SELECT_VALUE = '__empty__';

function setPath<T extends Record<string, unknown>>(obj: T, key: PropertyKey, value: unknown): T {
	return {...obj, [String(key)]: value};
}

function getDateFormat(format: string) {
	return format.replaceAll('Y', 'yyyy').replaceAll('m', 'MM').replaceAll('d', 'dd');
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

type TextFieldProps = {
	input: FieldInputProps<string | undefined, HTMLElement>;
	label: string;
	placeholder?: string;
	className?: string;
	autoFocus?: boolean;
	disabled?: boolean;
	isInvalid?: boolean;
	invalidText?: string;
};

const TextField: React.FC<TextFieldProps> = ({
	input,
	label,
	placeholder,
	className,
	autoFocus,
	disabled,
	isInvalid,
	invalidText,
}) => (
	<div className={className}>
		<Label htmlFor={input.name}>{label}</Label>
		<Input
			{...input}
			id={input.name}
			className="mt-1.5"
			placeholder={placeholder}
			autoFocus={autoFocus}
			disabled={disabled}
			aria-invalid={isInvalid}
			invalidText={invalidText}
		/>
	</div>
);

type DateFieldProps = {
	input: FieldInputProps<Date | undefined, HTMLElement>;
	id: string;
	label: string;
	placeholder: string;
	format: string;
};

const DateField: React.FC<DateFieldProps> = ({input, id, label, placeholder, format}) => {
	return (
		<div className="min-w-0 flex-1">
			<Label htmlFor={id}>{label}</Label>
			<DatePicker
				key={id}
				id={id}
				className="mt-1.5"
				value={input.value}
				onChange={input.onChange}
				placeholder={placeholder}
				format={format}
				aria-label={label}
			/>
		</div>
	);
};

const FieldsModal: React.FC<Props> = ({isOpen, onClose, onApply, onSave, onEdit, onDelete, initialValues}) => {
	const {t} = useTranslation();
	const label = t('tasklist.customFiltersModalAdvancedFiltersLabel');
	const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
	const isMultiTenancyEnabled = getClientConfig().deployment.isMultiTenancyEnabled;
	const groups = currentUser?.groups ?? [];
	const dateFormat = getDateFormat(t('globalWrittenCalendarDateFormat'));
	const getTenantId = useCallback(
		(formTenant: string | undefined) => {
			if (!isMultiTenancyEnabled) {
				return undefined;
			}

			if (formTenant !== undefined && formTenant !== '') {
				return formTenant;
			}

			if (formTenant === '') {
				return undefined;
			}

			if (currentUser.tenants.length === 1) {
				return currentUser.tenants[0]?.tenantId;
			}

			return undefined;
		},
		[currentUser, isMultiTenancyEnabled],
	);

	return (
		<Dialog
			open={isOpen}
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent
				size="md"
				showCloseButton={false}
				aria-label={t('tasklist.customFiltersModalAriaLabel')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
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
								<DialogHeader>
									<DialogTitle>{t('tasklist.customFiltersModalApplyFiltersTitle')}</DialogTitle>
									<Button
										type="button"
										variant="ghost"
										size="icon-sm"
										className="absolute top-2 right-2"
										aria-label={t('tasklist.optionsModalCloseButton')}
										onClick={onClose}
									>
										<X aria-hidden />
									</Button>
								</DialogHeader>
								<DialogBody>
									<form
										className="grid w-full grid-cols-2 items-end gap-x-3 gap-y-6"
										onSubmit={handleSubmit}
										tabIndex={-1}
									>
										{[undefined, 'custom'].includes(initialValues?.name) ? null : (
											<Field<string | undefined> name="name" defaultValue="">
												{({input}) => (
													<TextField
														input={input}
														label={t('tasklist.customFiltersModalFilterNameLabel')}
														className="col-span-2"
													/>
												)}
											</Field>
										)}

										<Field name="assignee">
											{({input}) => (
												<fieldset>
													<legend className="mb-2 text-sm font-medium">
														{t('tasklist.customFiltersModalAssigneeLabel')}
													</legend>
													<RadioGroup
														name={input.name}
														value={input.value}
														onValueChange={input.onChange}
														className="gap-2"
													>
														{[
															{value: 'all', label: t('tasklist.customFiltersModalAssigneeAll')},
															{value: 'unassigned', label: t('tasklist.customFiltersModalAssigneeUnassigned')},
															{value: 'me', label: t('tasklist.customFiltersModalAssigneeMe')},
															{
																value: 'user-and-group',
																label: t('tasklist.customFiltersModalAssigneeUserAndGround'),
															},
														].map(({value, label: optionLabel}, index) => (
															<Label key={value} className="flex items-center gap-2">
																<RadioGroupItem
																	value={value}
																	data-modal-primary-focus={index === 0 ? true : undefined}
																/>
																{optionLabel}
															</Label>
														))}
													</RadioGroup>
												</fieldset>
											)}
										</Field>

										<Field name="status">
											{({input}) => (
												<fieldset>
													<legend className="mb-2 text-sm font-medium">
														{t('tasklist.customFiltersModalStatusLabel')}
													</legend>
													<RadioGroup
														name={input.name}
														value={input.value}
														onValueChange={input.onChange}
														className="gap-2"
													>
														{[
															{value: 'all', label: t('tasklist.customFiltersModalStatusAll')},
															{value: 'open', label: t('tasklist.customFiltersModalStatusOpen')},
															{value: 'completed', label: t('tasklist.customFiltersModalStatusCompleted')},
														].map(({value, label: optionLabel}) => (
															<Label key={value} className="flex items-center gap-2">
																<RadioGroupItem value={value} />
																{optionLabel}
															</Label>
														))}
													</RadioGroup>
												</fieldset>
											)}
										</Field>

										{values?.assignee === 'user-and-group' ? (
											<>
												<Field<string | undefined> name="assignedTo">
													{({input}) => (
														<TextField
															input={input}
															label={t('tasklist.customFiltersModalAssignedToLabel')}
															placeholder={t('tasklist.customFiltersModalAssignedToPlaceholder')}
														/>
													)}
												</Field>
												<Field<string | undefined> name="candidateGroup">
													{({input}) =>
														groups.length === 0 ? (
															<TextField
																input={input}
																label={t('tasklist.customFiltersModalInAGroupLabel')}
																placeholder={t('tasklist.customFiltersModalInAGroupPlaceholder')}
																disabled={currentUser === undefined}
															/>
														) : (
															<div>
																<Label htmlFor={input.name}>{t('tasklist.customFiltersModalInAGroupLabel')}</Label>
																<Select
																	name={input.name}
																	value={
																		input.value === undefined || input.value === '' ? EMPTY_SELECT_VALUE : input.value
																	}
																	onValueChange={(value) => input.onChange(value === EMPTY_SELECT_VALUE ? '' : value)}
																>
																	<SelectTrigger id={input.name} className="mt-1.5 w-full" onBlur={input.onBlur}>
																		<SelectValue />
																	</SelectTrigger>
																	<SelectContent>
																		<SelectItem value={EMPTY_SELECT_VALUE}>&nbsp;</SelectItem>
																		{groups.map((group) => (
																			<SelectItem key={group} value={group}>
																				{group}
																			</SelectItem>
																		))}
																	</SelectContent>
																</Select>
															</div>
														)
													}
												</Field>
											</>
										) : null}

										<Field<string | undefined> name="bpmnProcess">
											{({input}) => (
												<ErrorBoundary FallbackComponent={ProcessesSelectErrorFallback}>
													<Suspense fallback={<Skeleton className="h-14 w-full" />}>
														<ProcessesSelect
															{...input}
															id={input.name}
															tenantId={getTenantId(values?.tenant)}
															labelText={t('tasklist.customFiltersModalLatestProcessVersionLabel')}
														/>
													</Suspense>
												</ErrorBoundary>
											)}
										</Field>
										{isMultiTenancyEnabled ? (
											<Field<string | undefined> name="tenant">
												{({input}) => (
													<MultitenancySelect
														{...input}
														id={input.name}
														labelText={t('tasklist.multiTenancyDropdownLabel')}
													/>
												)}
											</Field>
										) : null}

										<Field name="areAdvancedFiltersEnabled">
											{({input}) => (
												<div className="col-span-2 flex items-center gap-2">
													<Switch
														id="toggle-advanced-filters"
														size="sm"
														aria-label={label}
														checked={input.value}
														onCheckedChange={input.onChange}
													/>
													<Label htmlFor="toggle-advanced-filters">{label}</Label>
												</div>
											)}
										</Field>

										{values?.areAdvancedFiltersEnabled ? (
											<>
												<fieldset>
													<legend className="mb-2 text-sm font-medium">
														{t('tasklist.customFiltersModalDueDateLabel')}
													</legend>
													<div className="flex gap-2">
														<Field<Date | undefined> name="dueDateFrom">
															{({input}) => (
																<DateField
																	input={input}
																	id="due-date-from"
																	label={t('tasklist.customFiltersModalFromLabel')}
																	placeholder={t('globalDatePlaceholder')}
																	format={dateFormat}
																/>
															)}
														</Field>
														<Field<Date | undefined> name="dueDateTo">
															{({input}) => (
																<DateField
																	input={input}
																	id="due-date-to"
																	label={t('tasklist.customFiltersModalToLabel')}
																	placeholder={t('globalDatePlaceholder')}
																	format={dateFormat}
																/>
															)}
														</Field>
													</div>
												</fieldset>

												<fieldset>
													<legend className="mb-2 text-sm font-medium">
														{t('tasklist.customFiltersModalFollowUpDateLabel')}
													</legend>
													<div className="flex gap-2">
														<Field<Date | undefined> name="followUpDateFrom">
															{({input}) => (
																<DateField
																	input={input}
																	id="follow-up-date-from"
																	label={t('tasklist.customFiltersModalFromLabel')}
																	placeholder={t('globalDatePlaceholder')}
																	format={dateFormat}
																/>
															)}
														</Field>
														<Field<Date | undefined> name="followUpDateTo">
															{({input}) => (
																<DateField
																	input={input}
																	id="follow-up-date-to"
																	label={t('tasklist.customFiltersModalToLabel')}
																	placeholder={t('globalDatePlaceholder')}
																	format={dateFormat}
																/>
															)}
														</Field>
													</div>
												</fieldset>

												<Field<string | undefined> name="taskId">
													{({input}) => <TextField input={input} label={t('tasklist.customFiltersModalTaskIDLabel')} />}
												</Field>

												<AdvancedStringFilter
													name="businessId"
													label={t('tasklist.customFiltersModalBusinessIdLabel')}
													selectableOperators={['$eq', '$like', '$in']}
												/>

												<FieldArray name="variables">
													{({fields, meta: arrayMeta}) => (
														<fieldset className="col-span-2">
															<legend className="mb-3 text-sm font-medium">
																{t('tasklist.customFiltersModalTaskVariableLabel')}
															</legend>
															<div className="grid grid-cols-[1fr_1fr_2.25rem] items-start gap-x-3 gap-y-4">
																{fields.map((name, index) => (
																	<Fragment key={name}>
																		<Field<string | undefined> name={`${name}.name`}>
																			{({input, meta}) => (
																				<TextField
																					input={input}
																					label={t('tasklist.taskDetailsNewVariableNameFieldLabel')}
																					autoFocus={index === (fields.length ?? 1) - 1}
																					isInvalid={meta.submitError !== undefined && !arrayMeta.dirtySinceLastSubmit}
																					invalidText={meta.submitError}
																				/>
																			)}
																		</Field>

																		<Field<string | undefined> name={`${name}.value`}>
																			{({input, meta}) => (
																				<TextField
																					input={input}
																					label={t('tasklist.customFiltersModalVariableValueLabel')}
																					isInvalid={meta.submitError !== undefined && !arrayMeta.dirtySinceLastSubmit}
																					invalidText={meta.submitError}
																				/>
																			)}
																		</Field>

																		<Button
																			type="button"
																			variant="ghost"
																			size="icon"
																			className="mt-6"
																			aria-label={t('tasklist.customFiltersModalRemoveVariableButton')}
																			onClick={() => fields.remove(index)}
																		>
																			<X aria-hidden />
																		</Button>
																	</Fragment>
																))}
															</div>

															<Button
																type="button"
																variant="secondary"
																className="mt-4"
																aria-label={t('tasklist.customFiltersModalAddVariableButtonAria')}
																onClick={() => fields.push({name: '', value: ''})}
															>
																<Plus aria-hidden />
																{t('tasklist.customFiltersModalAddVariableButton')}
															</Button>
														</fieldset>
													)}
												</FieldArray>
											</>
										) : null}
									</form>
								</DialogBody>
								<DialogFooter className="grid grid-cols-[2fr_1fr_1fr_1fr] flex-nowrap">
									<Button
										variant="ghost"
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
											<Button variant="secondary" onClick={onClose} type="button">
												{t('tasklist.customFiltersModalCancelButton')}
											</Button>
											<Button
												variant="secondary"
												onClick={() => {
													form.change('action', 'save');
													form.submit();
												}}
												type="submit"
											>
												{t('tasklist.customFiltersModalSaveButton')}
											</Button>
											<Button
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
											<Button variant="secondary" onClick={() => onDelete(values?.name ?? '')} type="button">
												{t('tasklist.customFiltersModalDeleteButton')}
											</Button>
											<Button variant="secondary" onClick={onClose} type="button">
												{t('tasklist.customFiltersModalCancelButton')}
											</Button>
											<Button
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
								</DialogFooter>
							</>
						)}
					</Form>
				) : null}
			</DialogContent>
		</Dialog>
	);
};

export {FieldsModal};
