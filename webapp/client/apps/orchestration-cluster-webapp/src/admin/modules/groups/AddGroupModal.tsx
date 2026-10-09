/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, Form, type FieldMetaState} from 'react-final-form';
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
import {isValidId} from '#/admin/modules/shared/identifierPattern';
import {isDuplicateGroupIdError} from './isDuplicateGroupIdError';
import {useGroupMutations} from './useGroupMutations';

type FormValues = {
	groupId: string;
	name: string;
	description: string;
};

// The group ID is the only field that can also fail server-side (duplicate ID), surfaced via
// final-form's `submitError` rather than the synchronous `validate` error.
function getGroupIdError(meta: FieldMetaState<string>): string | undefined {
	if (meta.submitError && !meta.dirtySinceLastSubmit) {
		return meta.submitError;
	}
	return meta.touched ? meta.error : undefined;
}

type Props = {
	isOpen: boolean;
	onClose: () => void;
};

const AddGroupModal: React.FC<Props> = ({isOpen, onClose}) => {
	const {t} = useTranslation();
	const {create} = useGroupMutations();

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
				aria-label={t('admin.groups.createGroup')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{isOpen ? (
					<Form<FormValues>
						onSubmit={async (values) => {
							try {
								await create.mutateAsync(values);
								onClose();
								return undefined;
							} catch (error) {
								if (isDuplicateGroupIdError(error)) {
									return {groupId: t('admin.groups.groupIdAlreadyExists')};
								}
								return undefined;
							}
						}}
						validate={({groupId, name}) => ({
							groupId: !groupId
								? t('admin.groups.groupIdRequired')
								: isValidId(groupId)
									? undefined
									: t('admin.groups.groupIdInvalid'),
							name: name ? undefined : t('admin.groups.groupNameRequired'),
						})}
					>
						{({handleSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.groups.createGroup')}</DialogTitle>
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
									<form className="flex flex-col gap-4" onSubmit={handleSubmit}>
										<Field name="groupId">
											{({input, meta}) => {
												const errorMessage = getGroupIdError(meta);
												return (
													<div className="flex flex-col gap-1.5">
														<Label htmlFor="create-group-id">{t('admin.groups.groupId')}</Label>
														<Input
															id="create-group-id"
															placeholder={t('admin.groups.enterGroupId')}
															helperText={t('admin.groups.groupIdHelperText')}
															required
															autoFocus
															value={input.value}
															onChange={input.onChange}
															onBlur={input.onBlur}
															aria-invalid={Boolean(errorMessage)}
															invalidText={errorMessage}
														/>
													</div>
												);
											}}
										</Field>
										<Field name="name">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="create-group-name">{t('admin.groups.groupName')}</Label>
													<Input
														id="create-group-name"
														placeholder={t('admin.groups.enterGroupName')}
														required
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
													<Label htmlFor="create-group-description">{t('admin.groups.description')}</Label>
													<Textarea
														id="create-group-description"
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
									<Button type="button" loading={create.isPending} onClick={form.submit}>
										{t('admin.groups.createGroup')}
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

export {AddGroupModal};
