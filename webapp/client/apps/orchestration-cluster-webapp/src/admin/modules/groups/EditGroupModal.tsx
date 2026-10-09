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
	Textarea,
} from '@camunda/design-system';
import type {Group} from '@camunda/camunda-api-zod-schemas/8.11';
import {useGroupMutations} from './useGroupMutations';

type FormValues = {
	name: string;
	description: string;
};

type Props = {
	isOpen: boolean;
	group: Group;
	onClose: () => void;
};

const EditGroupModal: React.FC<Props> = ({isOpen, group, onClose}) => {
	const {t} = useTranslation();
	const {update} = useGroupMutations();

	const handleSubmit = (values: FormValues) => {
		update.mutate({groupId: group.groupId, name: values.name, description: values.description}, {onSuccess: onClose});
	};

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
				aria-label={t('admin.groups.editGroup')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{isOpen ? (
					<Form<FormValues>
						initialValues={{name: group.name, description: group.description}}
						onSubmit={handleSubmit}
						validate={({name}) => ({name: name ? undefined : t('admin.groups.groupNameRequired')})}
					>
						{({handleSubmit: handleFormSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.groups.editGroup')}</DialogTitle>
									<Button
										type="button"
										variant="ghost"
										size="icon-sm"
										className="absolute top-2 right-2"
										aria-label={t('admin.groups.cancel')}
										onClick={onClose}
									>
										<X aria-hidden />
									</Button>
								</DialogHeader>
								<DialogBody>
									<form className="flex flex-col gap-4" onSubmit={handleFormSubmit}>
										<div className="flex flex-col gap-1.5">
											<Label htmlFor="edit-group-id">{t('admin.groups.groupId')}</Label>
											<Input id="edit-group-id" value={group.groupId} readOnly />
										</div>
										<Field name="name">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="edit-group-name">{t('admin.groups.groupName')}</Label>
													<Input
														id="edit-group-name"
														placeholder={t('admin.groups.enterGroupName')}
														required
														autoFocus
														value={input.value}
														onChange={input.onChange}
														onBlur={input.onBlur}
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
													/>
												</div>
											)}
										</Field>
										<Field name="description">
											{({input}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="edit-group-description">{t('admin.groups.description')}</Label>
													<Textarea
														id="edit-group-description"
														placeholder={t('admin.groups.enterDescription')}
														value={input.value}
														onChange={input.onChange}
														onBlur={input.onBlur}
													/>
												</div>
											)}
										</Field>
									</form>
								</DialogBody>
								<DialogFooter>
									<Button type="button" variant="secondary" onClick={onClose}>
										{t('admin.groups.cancel')}
									</Button>
									<Button type="button" loading={update.isPending} onClick={form.submit}>
										{t('admin.groups.updateGroup')}
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

export {EditGroupModal};
