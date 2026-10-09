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
	Checkbox,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Input,
	Label,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
	Separator,
} from '@camunda/design-system';
import type {OwnerType, ResourceType} from '@camunda/camunda-api-zod-schemas/8.11';
import {OwnerSelection} from './OwnerSelection';
import {useAuthorizationMutations} from './useAuthorizationMutations';
import {
	AUTHORIZATION_WILDCARD,
	SECRET_NAME_PATTERN,
	SECRET_REFERENCE_PREFIX,
	getIdPattern,
	isValidId,
	isValidResourceId,
	isValidSecretResourceId,
} from './authorizationValidation';

const AUTHORIZATIONS_GUIDE_URL = 'https://docs.camunda.io/docs/next/components/admin/authorization/';
const OIDC_OWNER_TYPES: OwnerType[] = ['USER', 'GROUP', 'ROLE', 'MAPPING_RULE', 'CLIENT'];
const INTERNAL_OWNER_TYPES: OwnerType[] = ['USER', 'GROUP', 'ROLE'];
const USER_TASK_PROPERTY_NAMES = ['assignee', 'candidateGroups', 'candidateUsers'] as const;

type FormValues = {
	ownerType: OwnerType;
	ownerId: string;
	resourceId: string;
	resourcePropertyName: string;
	permissionTypes: string[];
};

type Props = {
	isOpen: boolean;
	onClose: () => void;
	resourceType: ResourceType;
	permissions: string[];
	idPattern: string | null | undefined;
	isOidc: boolean;
	isCamundaGroupsEnabled: boolean;
};

function getInitialValues(resourceType: ResourceType): FormValues {
	return {
		ownerType: 'USER',
		ownerId: '',
		resourceId: '',
		resourcePropertyName: resourceType === 'USER_TASK' ? USER_TASK_PROPERTY_NAMES[0] : '',
		permissionTypes: [],
	};
}

const AddAuthorizationModal: React.FC<Props> = ({
	isOpen,
	onClose,
	resourceType,
	permissions,
	idPattern,
	isOidc,
	isCamundaGroupsEnabled,
}) => {
	const {t} = useTranslation();
	const {create} = useAuthorizationMutations();
	const ownerTypes = isOidc ? OIDC_OWNER_TYPES : INTERNAL_OWNER_TYPES;
	const hasPermissions = permissions.length > 0;
	const longestPermissionLength = Math.max(0, ...permissions.map((permission) => permission.length));

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
				aria-label={t('admin.authorizations.addAuthorizationModalTitle')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{isOpen ? (
					<Form<FormValues>
						initialValues={getInitialValues(resourceType)}
						onSubmit={async ({ownerType, ownerId, resourceId, resourcePropertyName, permissionTypes}) => {
							try {
								await create.mutateAsync({
									ownerType,
									ownerId,
									resourceType,
									resourceId: resourceType === 'USER_TASK' ? null : resourceId,
									resourcePropertyName: resourceType === 'USER_TASK' ? resourcePropertyName : null,
									permissionTypes,
								});
								onClose();
							} catch {
								// The mutation already surfaces the failure as a toast; keep the modal open to retry.
							}
							return undefined;
						}}
						validate={({ownerId, resourceId, permissionTypes}) => {
							const pattern = getIdPattern(idPattern).toString();
							let resourceIdError: string | undefined;
							if (resourceType === 'SECRET') {
								resourceIdError = !resourceId
									? t('admin.authorizations.resourceIdRequiredError')
									: isValidSecretResourceId(resourceId)
										? undefined
										: t('admin.authorizations.secretResourceIdInvalidError', {pattern: SECRET_NAME_PATTERN.toString()});
							} else if (resourceType !== 'USER_TASK') {
								resourceIdError = !resourceId
									? t('admin.authorizations.resourceIdRequiredError')
									: isValidResourceId(resourceId, idPattern)
										? undefined
										: t('admin.authorizations.resourceIdInvalidError', {pattern});
							}

							return {
								ownerId: !ownerId
									? t('admin.authorizations.ownerRequiredError')
									: isValidId(ownerId, idPattern)
										? undefined
										: t('admin.authorizations.ownerInvalidError', {pattern}),
								resourceId: resourceIdError,
								permissionTypes:
									permissionTypes.length === 0 ? t('admin.authorizations.permissionsRequiredError') : undefined,
							};
						}}
					>
						{({handleSubmit, form, values}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.authorizations.addAuthorizationModalTitle')}</DialogTitle>
									<Button
										type="button"
										variant="ghost"
										size="icon-sm"
										className="absolute top-2 right-2"
										aria-label={t('admin.authorizations.cancelButton')}
										onClick={onClose}
									>
										<X aria-hidden />
									</Button>
								</DialogHeader>
								<DialogBody>
									<form onSubmit={handleSubmit} className="flex flex-col gap-4">
										<p className="text-sm leading-5 text-muted-foreground">
											{t('admin.authorizations.addAuthorizationIntro')}
											<Button asChild variant="link" className="h-auto p-0 align-baseline font-normal">
												<a href={AUTHORIZATIONS_GUIDE_URL} target="_blank" rel="noopener noreferrer">
													{t('admin.authorizations.learnMoreLinkLabel')}
												</a>
											</Button>
										</p>

										<Field<OwnerType> name="ownerType">
											{({input}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="authorization-owner-type">
														{t('admin.authorizations.ownerTypeFieldLabel')}
													</Label>
													<Select
														value={input.value}
														onValueChange={(next) => {
															input.onChange(next);
															form.change('ownerId', '');
															form.resetFieldState('ownerId');
														}}
													>
														<SelectTrigger id="authorization-owner-type" className="w-full">
															<SelectValue placeholder={t('admin.authorizations.ownerTypeFieldPlaceholder')} />
														</SelectTrigger>
														<SelectContent>
															{ownerTypes.map((ownerType) => (
																<SelectItem key={ownerType} value={ownerType}>
																	{ownerType}
																</SelectItem>
															))}
														</SelectContent>
													</Select>
												</div>
											)}
										</Field>

										<Field<string> name="ownerId">
											{({input, meta}) => (
												<OwnerSelection
													key={values.ownerType}
													ownerType={values.ownerType}
													value={input.value}
													onChange={input.onChange}
													onBlur={input.onBlur}
													error={meta.touched ? meta.error : undefined}
													isOidc={isOidc}
													isCamundaGroupsEnabled={isCamundaGroupsEnabled}
												/>
											)}
										</Field>

										<Separator />

										<div className="flex flex-col gap-1.5">
											<Label htmlFor="authorization-resource-type">
												{t('admin.authorizations.resourceTypeFieldLabel')}
											</Label>
											<Input id="authorization-resource-type" value={resourceType} disabled readOnly />
										</div>

										{resourceType === 'USER_TASK' ? (
											<Field<string> name="resourcePropertyName">
												{({input}) => (
													<div className="flex flex-col gap-1.5">
														<Label htmlFor="authorization-resource-property">
															{t('admin.authorizations.resourcePropertyNameFieldLabel')}
														</Label>
														<Select value={input.value} onValueChange={input.onChange}>
															<SelectTrigger id="authorization-resource-property" className="w-full">
																<SelectValue />
															</SelectTrigger>
															<SelectContent>
																{USER_TASK_PROPERTY_NAMES.map((name) => (
																	<SelectItem key={name} value={name}>
																		{name}
																	</SelectItem>
																))}
															</SelectContent>
														</Select>
													</div>
												)}
											</Field>
										) : resourceType === 'SECRET' ? (
											<Field<string> name="resourceId">
												{({input, meta}) => {
													const isWildcard = input.value === AUTHORIZATION_WILDCARD;
													const secretName = input.value.startsWith(SECRET_REFERENCE_PREFIX)
														? input.value.slice(SECRET_REFERENCE_PREFIX.length)
														: input.value;
													return (
														<div className="flex flex-col gap-1.5">
															<Label htmlFor="authorization-resource-id">
																{t('admin.authorizations.resourceIdFieldLabel')}
															</Label>
															<div className="flex items-center gap-1.5">
																{isWildcard ? null : (
																	<span className="shrink-0 text-sm text-muted-foreground">
																		{SECRET_REFERENCE_PREFIX}
																	</span>
																)}
																<Input
																	id="authorization-resource-id"
																	className="min-w-0 flex-1"
																	placeholder={t('admin.authorizations.secretNameFieldPlaceholder')}
																	value={isWildcard ? AUTHORIZATION_WILDCARD : secretName}
																	onChange={(event) => {
																		const typed = event.target.value;
																		input.onChange(
																			typed === ''
																				? ''
																				: typed === AUTHORIZATION_WILDCARD
																					? typed
																					: SECRET_REFERENCE_PREFIX + typed,
																		);
																	}}
																	onBlur={input.onBlur}
																	aria-invalid={Boolean(meta.touched && meta.error)}
																/>
															</div>
															<span className="text-xs text-muted-foreground">
																{t('admin.authorizations.secretResourceIdHelperText')}
															</span>
															{meta.touched && meta.error ? (
																<span role="alert" className="text-xs text-danger-foreground-subtle">
																	{meta.error}
																</span>
															) : null}
														</div>
													);
												}}
											</Field>
										) : (
											<Field<string> name="resourceId">
												{({input, meta}) => (
													<div className="flex flex-col gap-1.5">
														<Label htmlFor="authorization-resource-id">
															{t('admin.authorizations.resourceIdFieldLabel')}
														</Label>
														<Input
															id="authorization-resource-id"
															placeholder={t('admin.authorizations.resourceIdFieldPlaceholder')}
															value={input.value}
															onChange={input.onChange}
															onBlur={input.onBlur}
															aria-invalid={Boolean(meta.touched && meta.error)}
															invalidText={meta.touched ? meta.error : undefined}
														/>
													</div>
												)}
											</Field>
										)}

										<Separator />

										<Field<string[]> name="permissionTypes">
											{({input, meta}) => {
												const showError = !hasPermissions || (meta.submitFailed && Boolean(meta.error));
												return (
													<fieldset
														className="grid gap-x-4 gap-y-2"
														style={{
															gridTemplateColumns: `repeat(auto-fill, minmax(calc(${longestPermissionLength}ch + 3rem), 1fr))`,
														}}
													>
														<legend className="mb-1 text-sm text-muted-foreground">
															{t('admin.authorizations.permissionsFieldLabel')}
														</legend>
														{permissions.map((permission) => (
															<div key={permission} className="flex min-w-0 items-center gap-2">
																<Checkbox
																	id={`authorization-permission-${permission}`}
																	className="after:hidden"
																	checked={input.value.includes(permission)}
																	aria-invalid={showError}
																	onCheckedChange={(checked) =>
																		input.onChange(
																			checked === true
																				? [...input.value, permission]
																				: input.value.filter((selected) => selected !== permission),
																		)
																	}
																/>
																<Label
																	htmlFor={`authorization-permission-${permission}`}
																	className="min-w-0 break-words"
																>
																	{permission}
																</Label>
															</div>
														))}
														{showError ? (
															<span role="alert" className="col-span-full text-xs text-danger-foreground-subtle">
																{hasPermissions ? meta.error : t('admin.authorizations.permissionsUnavailable')}
															</span>
														) : null}
													</fieldset>
												);
											}}
										</Field>
									</form>
								</DialogBody>
								<DialogFooter>
									<Button type="button" variant="secondary" onClick={onClose}>
										{t('admin.authorizations.cancelButton')}
									</Button>
									<Button type="button" loading={create.isPending} disabled={!hasPermissions} onClick={form.submit}>
										{t('admin.authorizations.createButton')}
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

export {AddAuthorizationModal};
