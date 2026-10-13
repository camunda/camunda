/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Pencil, Trash2} from '@camunda/design-system/icons';
import {
	Breadcrumb,
	BreadcrumbItem,
	BreadcrumbLink,
	BreadcrumbList,
	BreadcrumbPage,
	BreadcrumbSeparator,
	Button,
	Card,
	CardContent,
	Label,
	PageHeader,
	PageLayout,
	Tabs,
	TabsContent,
	TabsList,
	TabsTrigger,
	Text,
} from '@camunda/design-system';
import type {Tenant} from '@camunda/camunda-api-zod-schemas/8.11';
import {EditTenantModal} from '#/admin/modules/tenants/EditTenantModal';
import {DeleteTenantModal} from '#/admin/modules/tenants/DeleteTenantModal';
import {TenantTabContent} from '#/admin/modules/tenants/TenantTabContent';
import {isDefaultTenant} from '#/admin/modules/tenants/defaultTenant';
import type {TenantDetailSearch, TenantTab} from '#/admin/modules/tenants/detailSearchSchema';

type ModalState = 'edit' | 'delete' | null;

type AdminTenantDetailPageProps = {
	tenant: Tenant;
	tabs: TenantTab[];
	isOidc: boolean;
	search: TenantDetailSearch;
	onSearchChange: (next: Partial<TenantDetailSearch>) => void;
	onDeleted: () => void;
};

const AdminTenantDetailPage: React.FC<AdminTenantDetailPageProps> = ({
	tenant,
	tabs,
	isOidc,
	search,
	onSearchChange,
	onDeleted,
}) => {
	const {t} = useTranslation();
	const [modalState, setModalState] = useState<ModalState>(null);
	const closeModal = () => setModalState(null);
	const activeTab = search.tab !== undefined && tabs.includes(search.tab) ? search.tab : (tabs[0] ?? 'users');

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<PageHeader
					title={tenant.name}
					breadcrumb={
						<Breadcrumb>
							<BreadcrumbList>
								<BreadcrumbItem>
									<BreadcrumbLink href="/admin/tenants">{t('admin.headerNavItemTenants')}</BreadcrumbLink>
								</BreadcrumbItem>
								<BreadcrumbSeparator />
								<BreadcrumbItem>
									<BreadcrumbPage>{tenant.name}</BreadcrumbPage>
								</BreadcrumbItem>
							</BreadcrumbList>
						</Breadcrumb>
					}
					actions={
						isDefaultTenant(tenant.tenantId) ? undefined : (
							<>
								<Button type="button" variant="secondary" onClick={() => setModalState('edit')}>
									<Pencil aria-hidden />
									{t('admin.tenants.editTenant')}
								</Button>
								<Button type="button" variant="destructive" onClick={() => setModalState('delete')}>
									<Trash2 aria-hidden />
									{t('admin.tenants.deleteTenant')}
								</Button>
							</>
						)
					}
				/>

				<Card className="max-w-md">
					<CardContent className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-4">
						<Label>{t('admin.tenants.tenantIdColumn')}</Label>
						<Text as="p">{tenant.tenantId}</Text>
						<Label>{t('admin.tenants.tenantNameColumn')}</Label>
						<Text as="p">{tenant.name}</Text>
						<Label>{t('admin.tenants.descriptionFieldLabel')}</Label>
						<Text as="p">{tenant.description || '-'}</Text>
					</CardContent>
				</Card>

				<Tabs
					value={activeTab}
					onValueChange={(tab) =>
						onSearchChange({
							tab: tab as TenantTab,
							page: undefined,
							pageSize: undefined,
							sortOrder: undefined,
						})
					}
				>
					<TabsList variant="line">
						{tabs.map((tab) => (
							<TabsTrigger key={tab} value={tab}>
								{t(`admin.tenants.tab_${tab}`)}
							</TabsTrigger>
						))}
					</TabsList>
					<TabsContent value={activeTab}>
						<Suspense fallback={null}>
							<TenantTabContent
								tab={activeTab}
								tenantId={tenant.tenantId}
								search={search}
								isOidc={isOidc}
								onSearchChange={onSearchChange}
							/>
						</Suspense>
					</TabsContent>
				</Tabs>
			</div>

			<EditTenantModal tenant={modalState === 'edit' ? tenant : null} onClose={closeModal} />
			<DeleteTenantModal tenant={modalState === 'delete' ? tenant : null} onClose={closeModal} onDeleted={onDeleted} />
		</PageLayout>
	);
};

export {AdminTenantDetailPage};
export type {AdminTenantDetailPageProps};
