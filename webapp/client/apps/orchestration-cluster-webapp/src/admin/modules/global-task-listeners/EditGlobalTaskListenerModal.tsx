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
	GlobalTaskListener,
	GlobalTaskListenerEventType,
	UpdateGlobalTaskListenerRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {isValidId} from '#/admin/modules/shared/identifierPattern';
import {useGlobalTaskListenerMutations} from './useGlobalTaskListenerMutations';
import {getEventTypeOptions, syncAllEventType, toFormEventTypes, toRequestEventTypes} from './eventTypes';

type FormValues = {
	type: string;
	eventTypes: GlobalTaskListenerEventType[];
	retries: number;
	afterNonGlobal: string;
	priority: number;
};

function toFormValues(globalTaskListener: GlobalTaskListener): FormValues {
	return {
		type: globalTaskListener.type,
		eventTypes: toFormEventTypes(globalTaskListener.eventTypes),
		retries: globalTaskListener.retries ?? 1,
		afterNonGlobal: String(globalTaskListener.afterNonGlobal ?? false),
		priority: globalTaskListener.priority ?? 0,
	};
}

type Props = {
	globalTaskListener: GlobalTaskListener | null;
	onClose: () => void;
};

const EditGlobalTaskListenerModal: React.FC<Props> = ({globalTaskListener, onClose}) => {
	const {t} = useTranslation();
	const {update} = useGlobalTaskListenerMutations();
	const isOpen = globalTaskListener !== null;
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
				aria-label={t('admin.globalTaskListeners.editGlobalTaskListenerModalTitle')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{globalTaskListener !== null ? (
					<Form<FormValues>
						initialValues={toFormValues(globalTaskListener)}
						onSubmit={(values: FormValues) => {
							const body: UpdateGlobalTaskListenerRequestBody = {
								type: values.type,
								eventTypes: toRequestEventTypes(values.eventTypes),
								retries: values.retries,
								afterNonGlobal: values.afterNonGlobal === 'true',
								priority: values.priority,
							};
							update.mutate({id: globalTaskListener.id, ...body}, {onSuccess: onClose});
						}}
						validate={({type, eventTypes, retries, priority}) => ({
							type: !type
								? t('admin.globalTaskListeners.listenerTypeRequiredError')
								: isValidId(type)
									? undefined
									: t('admin.globalTaskListeners.listenerTypeInvalidError'),
							eventTypes: eventTypes.length > 0 ? undefined : t('admin.globalTaskListeners.eventTypeRequiredError'),
							retries:
								Number.isInteger(retries) && retries >= 1 ? undefined : t('admin.globalTaskListeners.retriesMinError'),
							priority:
								Number.isInteger(priority) && priority >= 0
									? undefined
									: t('admin.globalTaskListeners.priorityMinError'),
						})}
					>
						{({handleSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.globalTaskListeners.editGlobalTaskListenerModalTitle')}</DialogTitle>
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
										<div className="flex flex-col gap-1.5">
											<Label htmlFor="globalTaskListenerId">
												{t('admin.globalTaskListeners.globalTaskListenerIdFieldLabel')}
											</Label>
											<Input id="globalTaskListenerId" value={globalTaskListener.id} disabled readOnly />
										</div>
										<Field name="type">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="listenerType">{t('admin.globalTaskListeners.listenerTypeFieldLabel')}</Label>
													<Input
														id="listenerType"
														value={input.value}
														onChange={input.onChange}
														autoFocus
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
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
									<Button type="button" loading={update.isPending} onClick={form.submit}>
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

export {EditGlobalTaskListenerModal};
