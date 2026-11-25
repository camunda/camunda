/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Field, Form} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {ArrowRight, X} from '@camunda/design-system/icons';
import {
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Heading,
	Input,
	Label,
	Textarea,
} from '@camunda/design-system';
import type {CreateTenantRequestBody, Tenant} from '@camunda/camunda-api-zod-schemas/8.11';
import {getAdminSectionConfig} from '#/admin/adminSections';
import {GUIDE_URLS} from '#/admin/modules/shared/guideUrls';
import {getAvailableTenantTabs} from './getAvailableTenantTabs';
import type {TenantTab} from './detailSearchSchema';
import {useTenantMutations} from './useTenantMutations';
import {MAX_DESCRIPTION_LENGTH, isValidTenantId} from './tenantValidation';

type Props = {
	onClose: () => void;
	onAssign: (tenantId: string, tab: TenantTab) => void;
};

const NEXT_STEPS = [
	{tab: 'users', kind: 'users'},
	{tab: 'groups', kind: 'groups'},
	{tab: 'roles', kind: 'roles'},
	{tab: 'mapping-rules', kind: 'mappingRules'},
	{tab: 'clients', kind: 'clients'},
] as const satisfies readonly {tab: TenantTab; kind: string}[];

function getNextSteps() {
	const availableTabs = getAvailableTenantTabs(getAdminSectionConfig());
	return NEXT_STEPS.filter(({tab}) => availableTabs.includes(tab));
}

type NextStepsProps = {
	tenant: Tenant;
	onClose: () => void;
	onAssign: (tenantId: string, tab: TenantTab) => void;
};

const NextSteps: React.FC<NextStepsProps> = ({tenant, onClose, onAssign}) => {
	const {t} = useTranslation();
	const nextSteps = getNextSteps();

	return (
		<>
			<DialogHeader>
				<DialogTitle>{t('admin.tenants.tenantCreatedTitle')}</DialogTitle>
			</DialogHeader>
			<DialogBody>
				<div className="flex flex-col gap-4">
					<p>{t('admin.tenants.tenantCreatedDescription', {name: tenant.name})}</p>
					<Heading as="h3" variant="heading-xs">
						{t('admin.tenants.nextStepHeading')}
					</Heading>
					<ul className="flex flex-col gap-2">
						{nextSteps.map(({tab, kind}, index) => (
							<li key={tab}>
								<Button
									type="button"
									autoFocus={index === 0}
									variant="ghost"
									className="w-full justify-between"
									onClick={() => {
										onClose();
										onAssign(tenant.tenantId, tab);
									}}
								>
									{t(`admin.tenants.members.${kind}.assign`)}
									<ArrowRight data-icon="inline-end" aria-hidden />
								</Button>
							</li>
						))}
					</ul>
					{nextSteps.some(({tab}) => tab === 'clients') && (
						<p className="text-sm text-muted-foreground">
							{t('admin.tenants.connectorRoleInfo')}{' '}
							<a href={GUIDE_URLS.tenants} target="_blank" rel="noopener noreferrer" className="underline">
								{t('admin.tenants.connectorRoleInfoLink')}
							</a>
						</p>
					)}
				</div>
			</DialogBody>
			<DialogFooter>
				<Button type="button" onClick={onClose}>
					{t('admin.tenants.gotItButton')}
				</Button>
			</DialogFooter>
		</>
	);
};

const AddTenantModal: React.FC<Props> = ({onClose, onAssign}) => {
	const {t} = useTranslation();
	const {create} = useTenantMutations();
	const [createdTenant, setCreatedTenant] = useState<Tenant | null>(null);

	return (
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent
				size="sm"
				showCloseButton={false}
				aria-label={t(
					createdTenant === null ? 'admin.tenants.addTenantModalTitle' : 'admin.tenants.tenantCreatedTitle',
				)}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{createdTenant !== null ? (
					<NextSteps tenant={createdTenant} onClose={onClose} onAssign={onAssign} />
				) : (
					<Form<CreateTenantRequestBody>
						onSubmit={(values) => {
							create.mutate(values, {onSuccess: setCreatedTenant});
						}}
						validate={({tenantId, name, description}) => ({
							tenantId: !tenantId
								? t('admin.tenants.tenantIdRequiredError')
								: isValidTenantId(tenantId)
									? undefined
									: t('admin.tenants.tenantIdInvalidError'),
							name: name ? undefined : t('admin.tenants.tenantNameRequiredError'),
							description:
								description !== undefined && description.length > MAX_DESCRIPTION_LENGTH
									? t('admin.tenants.descriptionTooLongError', {max: MAX_DESCRIPTION_LENGTH})
									: undefined,
						})}
					>
						{({handleSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('admin.tenants.addTenantModalTitle')}</DialogTitle>
									<Button
										type="button"
										variant="ghost"
										size="icon-sm"
										className="absolute top-2 right-2"
										aria-label={t('admin.tenants.cancel')}
										onClick={onClose}
									>
										<X aria-hidden />
									</Button>
								</DialogHeader>
								<DialogBody>
									<form onSubmit={handleSubmit} className="flex flex-col gap-4">
										<Field name="tenantId">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="tenantId">{t('admin.tenants.tenantIdFieldLabel')}</Label>
													<Input
														id="tenantId"
														placeholder={t('admin.tenants.tenantIdFieldPlaceholder')}
														helperText={t('admin.tenants.tenantIdHelperText')}
														value={input.value}
														onChange={input.onChange}
														autoFocus
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
													/>
												</div>
											)}
										</Field>
										<Field name="name">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="tenantName">{t('admin.tenants.tenantNameFieldLabel')}</Label>
													<Input
														id="tenantName"
														placeholder={t('admin.tenants.tenantNameFieldPlaceholder')}
														value={input.value}
														onChange={input.onChange}
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.touched ? meta.error : undefined}
													/>
												</div>
											)}
										</Field>
										<Field name="description">
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="tenantDescription">{t('admin.tenants.descriptionFieldLabel')}</Label>
													<Textarea
														id="tenantDescription"
														placeholder={t('admin.tenants.descriptionFieldPlaceholder', {max: MAX_DESCRIPTION_LENGTH})}
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
										{t('admin.tenants.cancel')}
									</Button>
									<Button type="button" loading={create.isPending} onClick={form.submit}>
										{t('admin.tenants.createButton')}
									</Button>
								</DialogFooter>
							</>
						)}
					</Form>
				)}
			</DialogContent>
		</Dialog>
	);
};

export {AddTenantModal};
