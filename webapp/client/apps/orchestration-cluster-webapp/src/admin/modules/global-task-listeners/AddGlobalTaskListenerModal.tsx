/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, Form} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {X} from '@camunda/design-system/icons';
import {
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Input,
	Label,
	MultiSelect,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from '@camunda/design-system';
import type {
	CreateGlobalTaskListenerRequestBody,
	GlobalTaskListenerEventType,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {useGlobalTaskListenerMutations} from './useGlobalTaskListenerMutations';
import {isValidId} from '#/admin/modules/shared/identifierPattern';
import {getEventTypeOptions, syncAllEventType, toRequestEventTypes} from './eventTypes';

type FormValues = {
	id: string;
	type: string;
	eventTypes: GlobalTaskListenerEventType[];
	retries: number;
	afterNonGlobal: string;
	priority: number;
};

const INITIAL_VALUES: FormValues = {
	id: '',
	type: '',
	eventTypes: [],
	retries: 3,
	afterNonGlobal: 'false',
	priority: 50,
};

type Props = {
	isOpen: boolean;
	onClose: () => void;
};

const AddGlobalTaskListenerModal: React.FC<Props> = ({isOpen, onClose}) => {
	const {t} = useTranslation();
	const {create} = useGlobalTaskListenerMutations();
	const eventTypeOptions = getEventTypeOptions(t);

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
				size="sm"
				showCloseButton={false}
				aria-label={t('admin.globalTaskListeners.addGlobalTaskListenerModalTitle')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{isOpen ? (
					<Form<FormValues>
						initialValues={INITIAL_VALUES}
						onSubmit={(values: FormValues) => {
							const body: CreateGlobalTaskListenerRequestBody = {
								id: values.id,
								type: values.type,
								eventTypes: toRequestEventTypes(values.eventTypes),
								retries: values.retries,
								afterNonGlobal: values.afterNonGlobal === 'true',
								priority: values.priority,
							};
							create.mutate(body, {onSuccess: onClose});
						}}
						validate={({id, type, eventTypes, retries, priority}) => ({
							id: !id
								? t('admin.globalTaskListeners.globalTaskListenerIdRequiredError')
								: isValidId(id)
									? undefined
									: t('admin.globalTaskListeners.globalTaskListenerIdInvalidError'),
							type: !type
								? t('admin.globalTaskListeners.listenerTypeRequiredError')
								: isValidId(type)
									? undefined
									: t('admin.globalTaskListeners.listenerTypeInvalidError'),
							eventTypes: eventTypes.length > 0 ? undefined : t('admin.globalTaskListeners.eventTypeRequiredError'),
							retries: retries >= 1 ? undefined : t('admin.globalTaskListeners.retriesMinError'),
							priority: priority >= 0 ? undefined : t('admin.globalTaskListeners.priorityMinError'),
						})}
					>
						{({handleSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.globalTaskListeners.addGlobalTaskListenerModalTitle')}</DialogTitle>
									<Button
										type="button"
										variant="ghost"
										size="icon-sm"
										className="absolute top-2 right-2"
										aria-label={t('admin.globalTaskListeners.cancelButton')}
										onClick={onClose}
									>
										<X aria-hidden />
									</Button>
								</DialogHeader>
								<DialogBody>
									<form onSubmit={handleSubmit} className="flex flex-col gap-4">
										<Field name="id">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="globalTaskListenerId">
														{t('admin.globalTaskListeners.globalTaskListenerIdFieldLabel')}
													</Label>
													<Input
														id="globalTaskListenerId"
														placeholder={t('admin.globalTaskListeners.globalTaskListenerIdFieldPlaceholder')}
														value={input.value}
														onChange={input.onChange}
														autoFocus
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
														helperText={
															meta.touched && meta.error
																? undefined
																: t('admin.globalTaskListeners.globalTaskListenerIdFieldHelperText')
														}
													/>
												</div>
											)}
										</Field>
										<Field name="type">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="listenerType">{t('admin.globalTaskListeners.listenerTypeFieldLabel')}</Label>
													<Input
														id="listenerType"
														placeholder={t('admin.globalTaskListeners.listenerTypeFieldPlaceholder')}
														value={input.value}
														onChange={input.onChange}
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
														helperText={
															meta.touched && meta.error
																? undefined
																: t('admin.globalTaskListeners.listenerTypeFieldHelperText')
														}
													/>
												</div>
											)}
										</Field>
										<Field name="eventTypes">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label id="eventTypesLabel">{t('admin.globalTaskListeners.eventTypeFieldLabel')}</Label>
													<MultiSelect
														id="eventTypes"
														aria-labelledby="eventTypesLabel"
														placeholder={t('admin.globalTaskListeners.selectEventTypesPlaceholder')}
														options={eventTypeOptions}
														sorted={false}
														hideSelectAll
														searchable={false}
														maxCount={eventTypeOptions.length}
														value={input.value}
														onValueChange={(value) => input.onChange(syncAllEventType(value, input.value))}
														aria-invalid={Boolean(meta.error && meta.touched)}
													/>
													{meta.touched && meta.error ? (
														<span className="text-destructive text-sm">{meta.error}</span>
													) : null}
												</div>
											)}
										</Field>
										<Field name="retries" parse={(value) => Number(value)}>
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="retries">{t('admin.globalTaskListeners.retriesFieldLabel')}</Label>
													<Input
														id="retries"
														type="number"
														min={1}
														step={1}
														value={input.value}
														onChange={input.onChange}
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
														helperText={
															meta.touched && meta.error
																? undefined
																: t('admin.globalTaskListeners.retriesFieldHelperText')
														}
													/>
												</div>
											)}
										</Field>
										<Field name="afterNonGlobal">
											{({input}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="afterNonGlobal">
														{t('admin.globalTaskListeners.executionOrderFieldLabel')}
													</Label>
													<Select value={input.value} onValueChange={input.onChange}>
														<SelectTrigger id="afterNonGlobal" className="w-full">
															<SelectValue />
														</SelectTrigger>
														<SelectContent>
															<SelectItem value="false">
																{t('admin.globalTaskListeners.executionOrderBefore')}
															</SelectItem>
															<SelectItem value="true">{t('admin.globalTaskListeners.executionOrderAfter')}</SelectItem>
														</SelectContent>
													</Select>
												</div>
											)}
										</Field>
										<Field name="priority" parse={(value) => Number(value)}>
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="priority">{t('admin.globalTaskListeners.priorityFieldLabel')}</Label>
													<Input
														id="priority"
														type="number"
														min={0}
														step={1}
														value={input.value}
														onChange={input.onChange}
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
														helperText={
															meta.touched && meta.error
																? undefined
																: t('admin.globalTaskListeners.priorityFieldHelperText')
														}
													/>
												</div>
											)}
										</Field>
									</form>
								</DialogBody>
								<DialogFooter>
									<Button type="button" variant="secondary" onClick={onClose}>
										{t('admin.globalTaskListeners.cancelButton')}
									</Button>
									<Button type="button" loading={create.isPending} onClick={form.submit}>
										{t('admin.globalTaskListeners.saveButton')}
									</Button>
								</DialogFooter>
							</>
						)}
					</Form>
				) : null}
			</DialogContent>
		</Dialog>
	);
};

export {AddGlobalTaskListenerModal};
