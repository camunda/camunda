/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Link} from '@tanstack/react-router';
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
	PageHeader,
	PageLayout,
	Tabs,
	TabsContent,
	TabsList,
	TabsTrigger,
} from '@camunda/design-system';
import type {Role} from '@camunda/camunda-api-zod-schemas/8.11';
import {DeleteRoleModal} from '#/admin/modules/roles/DeleteRoleModal';
import {EditRoleModal} from '#/admin/modules/roles/EditRoleModal';
import {RoleMembersTab} from '#/admin/modules/roles/RoleMembersTab';
import {MEMBER_KINDS, type MemberKind} from '#/admin/modules/roles/members';

type ModalState = {type: 'edit'} | {type: 'delete'} | null;

const OIDC_ONLY_KINDS: readonly MemberKind[] = ['mappingRules', 'clients'];

type AdminRoleDetailPageProps = {
	role: Role;
	isOidc: boolean;
	isCamundaGroupsEnabled: boolean;
	defaultRoleIds: string[];
	activeTab: MemberKind;
	onTabChange: (tab: MemberKind) => void;
	onDeleted: () => void;
};

const AdminRoleDetailPage: React.FC<AdminRoleDetailPageProps> = ({
	role,
	isOidc,
	isCamundaGroupsEnabled,
	defaultRoleIds,
	activeTab,
	onTabChange,
	onDeleted,
}) => {
	const {t} = useTranslation();
	const [modalState, setModalState] = useState<ModalState>(null);
	const isDefaultRole = defaultRoleIds.includes(role.roleId);
	const closeModal = () => setModalState(null);
	const visibleKinds = MEMBER_KINDS.filter((kind) => isOidc || !OIDC_ONLY_KINDS.includes(kind));
	const selectedTab = visibleKinds.includes(activeTab) ? activeTab : 'users';

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<PageHeader
					title={role.name}
					breadcrumb={
						<Breadcrumb>
							<BreadcrumbList>
								<BreadcrumbItem>
									<BreadcrumbLink asChild>
										<Link to="/admin/roles">{t('admin.headerNavItemRoles')}</Link>
									</BreadcrumbLink>
								</BreadcrumbItem>
								<BreadcrumbSeparator />
								<BreadcrumbItem>
									<BreadcrumbPage>{role.name}</BreadcrumbPage>
								</BreadcrumbItem>
							</BreadcrumbList>
						</Breadcrumb>
					}
					actions={
						!isDefaultRole && (
							<>
								<Button type="button" variant="secondary" onClick={() => setModalState({type: 'edit'})}>
									<Pencil aria-hidden />
									{t('admin.roles.editRole')}
								</Button>
								<Button type="button" variant="destructive" onClick={() => setModalState({type: 'delete'})}>
									<Trash2 aria-hidden />
									{t('admin.roles.deleteRole')}
								</Button>
							</>
						)
					}
				/>
				{role.description ? <p className="text-sm leading-5 text-muted-foreground">{role.description}</p> : null}

				<Tabs value={selectedTab} onValueChange={(value) => onTabChange(value as MemberKind)}>
					<TabsList aria-label={t('admin.roles.members.tabsLabel')}>
						{visibleKinds.map((kind) => (
							<TabsTrigger key={kind} value={kind}>
								{t(`admin.roles.members.${kind}.tab`)}
							</TabsTrigger>
						))}
					</TabsList>
					<TabsContent value={selectedTab} className="pt-4">
						<RoleMembersTab
							key={selectedTab}
							roleId={role.roleId}
							kind={selectedTab}
							isOidc={isOidc}
							isCamundaGroupsEnabled={isCamundaGroupsEnabled}
						/>
					</TabsContent>
				</Tabs>
			</div>

			{modalState?.type === 'edit' && <EditRoleModal isOpen role={role} onClose={closeModal} />}
			{modalState?.type === 'delete' && (
				<DeleteRoleModal isOpen roleId={role.roleId} onClose={closeModal} onDeleted={onDeleted} />
			)}
		</PageLayout>
	);
};

export {AdminRoleDetailPage};
export type {AdminRoleDetailPageProps};
