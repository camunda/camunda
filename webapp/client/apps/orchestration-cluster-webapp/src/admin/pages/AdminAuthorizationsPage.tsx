/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
	Button,
	Label,
	PageHeader,
	PageLayout,
	SearchInput,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from '@camunda/design-system';
import type {Authorization, ResourceType} from '@camunda/camunda-api-zod-schemas/8.11';
import {useDebouncedUrlFilter} from '#/shared/hooks/useDebouncedUrlFilter';
import type {AdminClientConfig} from '#/shared/http/adminClientConfig';
import {AddAuthorizationModal} from '#/admin/modules/authorizations/AddAuthorizationModal';
import {DeleteAuthorizationModal} from '#/admin/modules/authorizations/DeleteAuthorizationModal';
import {AuthorizationsTable} from '#/admin/modules/authorizations/AuthorizationsTable';
import type {AuthorizationsSearch} from '#/admin/modules/authorizations/searchSchema';

const AUTHORIZATIONS_GUIDE_URL = 'https://docs.camunda.io/docs/next/components/admin/authorization/';

type ModalState = {type: 'create'} | {type: 'delete'; authorization: Authorization} | null;

type AdminAuthorizationsPageProps = {
	search: AuthorizationsSearch;
	resourceType: ResourceType;
	availableResourceTypes: ResourceType[];
	clientConfig: AdminClientConfig;
	isOidc: boolean;
	isCamundaGroupsEnabled: boolean;
	onSearchChange: (next: Partial<AuthorizationsSearch>) => void;
};

const AdminAuthorizationsPage: React.FC<AdminAuthorizationsPageProps> = ({
	search,
	resourceType,
	availableResourceTypes,
	clientConfig,
	isOidc,
	isCamundaGroupsEnabled,
	onSearchChange,
}) => {
	const {t} = useTranslation();
	const appliedOwnerId = search.ownerId ?? '';
	const [ownerIdDraft, setOwnerIdDraft] = useDebouncedUrlFilter(appliedOwnerId, (ownerId) =>
		onSearchChange({ownerId, page: undefined}),
	);
	const [modalState, setModalState] = useState<ModalState>(null);

	const closeModal = useCallback(() => setModalState(null), []);

	const openDeleteModal = useCallback(
		(authorization: Authorization) => setModalState({type: 'delete', authorization}),
		[],
	);

	const title = t('admin.headerNavItemAuthorizations');

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<div className="flex flex-col gap-1">
					<PageHeader title={title} />
					<p className="text-sm leading-5 text-muted-foreground">
						{t('admin.authorizations.guideBody')}
						<Button asChild variant="link" className="h-auto p-0 align-baseline font-normal">
							<a href={AUTHORIZATIONS_GUIDE_URL} target="_blank" rel="noopener noreferrer">
								{t('admin.authorizations.guideLinkLabel')}
							</a>
						</Button>
					</p>
				</div>

				<div className="flex flex-col gap-4">
					<div className="flex items-end gap-4">
						<div className="flex flex-col gap-1">
							<Label htmlFor="authorizations-resource-type">{t('admin.authorizations.resourceTypeFilterLabel')}</Label>
							<Select
								value={resourceType}
								onValueChange={(next) => onSearchChange({resourceType: next as ResourceType, page: undefined})}
							>
								<SelectTrigger id="authorizations-resource-type" className="w-64">
									<SelectValue />
								</SelectTrigger>
								<SelectContent>
									{availableResourceTypes.map((type) => (
										<SelectItem key={type} value={type}>
											{type}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
						<div className="flex-1">
							<Label htmlFor="authorizations-owner-search" className="sr-only">
								{t('admin.authorizations.filterByOwnerId')}
							</Label>
							<SearchInput
								id="authorizations-owner-search"
								className="max-w-sm min-w-48"
								placeholder={t('admin.authorizations.filterByOwnerId')}
								value={ownerIdDraft}
								onChange={(event) => setOwnerIdDraft(event.target.value)}
								onClear={() => setOwnerIdDraft('')}
							/>
						</div>
						<Button type="button" onClick={() => setModalState({type: 'create'})}>
							{t('admin.authorizations.createAuthorization')}
						</Button>
					</div>
					<AuthorizationsTable
						search={search}
						resourceType={resourceType}
						clientConfig={clientConfig}
						onSearchChange={onSearchChange}
						onDelete={openDeleteModal}
					/>
				</div>
			</div>

			{modalState?.type === 'create' && (
				<AddAuthorizationModal
					isOpen
					onClose={closeModal}
					resourceType={resourceType}
					permissions={clientConfig.resourcePermissions[resourceType] ?? []}
					idPattern={clientConfig.idPattern}
					isOidc={isOidc}
					isCamundaGroupsEnabled={isCamundaGroupsEnabled}
				/>
			)}
			<DeleteAuthorizationModal
				authorization={modalState?.type === 'delete' ? modalState.authorization : null}
				onClose={closeModal}
			/>
		</PageLayout>
	);
};

export {AdminAuthorizationsPage};
export type {AdminAuthorizationsPageProps};
