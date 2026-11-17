/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense, useState} from 'react';
import {useQueryClient} from '@tanstack/react-query';
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
import type {Group} from '@camunda/camunda-api-zod-schemas/8.11';
import {DeleteGroupModal} from '#/admin/modules/groups/DeleteGroupModal';
import {EditGroupModal} from '#/admin/modules/groups/EditGroupModal';
import {GroupTabContent} from '#/admin/modules/groups/GroupTabContent';
import type {GroupDetailSearch, GroupDetailSearchChange} from '#/admin/modules/groups/detailSearchSchema';
import {
	getActiveMemberKind,
	getMembersQuery,
	getVisibleMemberKinds,
	type MemberKind,
} from '#/admin/modules/groups/members';
import {DEFAULT_PAGE_SIZE} from '#/admin/modules/groups/searchSchema';

type ModalState = {type: 'edit'} | {type: 'delete'} | null;

type AdminGroupDetailPageProps = {
	group: Group;
	isOidc: boolean;
	canManageMappingRules: boolean;
	search: GroupDetailSearch;
	onSearchChange: GroupDetailSearchChange;
	onDeleted: () => void;
};

const AdminGroupDetailPage: React.FC<AdminGroupDetailPageProps> = ({
	group,
	isOidc,
	canManageMappingRules,
	search,
	onSearchChange,
	onDeleted,
}) => {
	const {t} = useTranslation();
	const queryClient = useQueryClient();
	const [modalState, setModalState] = useState<ModalState>(null);
	const closeModal = () => setModalState(null);
	const visibleKinds = getVisibleMemberKinds({isOidc, canManageMappingRules});
	const selectedTab = getActiveMemberKind(visibleKinds, search.tab);
	const prefetchTab = (kind: MemberKind) => {
		queryClient
			.query(getMembersQuery(kind, group.groupId, {page: {from: 0, limit: DEFAULT_PAGE_SIZE}}))
			.catch(() => undefined);
	};

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<PageHeader
					title={group.name}
					breadcrumb={
						<Breadcrumb>
							<BreadcrumbList>
								<BreadcrumbItem>
									<BreadcrumbLink asChild>
										<Link to="/admin/groups">{t('admin.headerNavItemGroups')}</Link>
									</BreadcrumbLink>
								</BreadcrumbItem>
								<BreadcrumbSeparator />
								<BreadcrumbItem>
									<BreadcrumbPage>{group.name}</BreadcrumbPage>
								</BreadcrumbItem>
							</BreadcrumbList>
						</Breadcrumb>
					}
					actions={
						<>
							<Button type="button" variant="secondary" onClick={() => setModalState({type: 'edit'})}>
								<Pencil aria-hidden />
								{t('admin.groups.editGroup')}
							</Button>
							<Button type="button" variant="destructive" onClick={() => setModalState({type: 'delete'})}>
								<Trash2 aria-hidden />
								{t('admin.groups.deleteGroup')}
							</Button>
						</>
					}
				/>
				{group.description ? <p className="text-sm leading-5 text-muted-foreground">{group.description}</p> : null}

				<Tabs
					value={selectedTab}
					onValueChange={(value) => onSearchChange({tab: value as MemberKind, page: undefined, pageSize: undefined})}
				>
					<TabsList aria-label={t('admin.groups.members.tabsLabel')}>
						{visibleKinds.map((kind) => (
							<TabsTrigger
								key={kind}
								value={kind}
								onPointerEnter={() => prefetchTab(kind)}
								onFocus={() => prefetchTab(kind)}
							>
								{t(`admin.groups.members.${kind}.tab`)}
							</TabsTrigger>
						))}
					</TabsList>
					<TabsContent value={selectedTab} className="pt-4">
						<Suspense fallback={null}>
							<GroupTabContent
								key={selectedTab}
								groupId={group.groupId}
								kind={selectedTab}
								isOidc={isOidc}
								search={search}
								onSearchChange={onSearchChange}
							/>
						</Suspense>
					</TabsContent>
				</Tabs>
			</div>

			{modalState?.type === 'edit' && <EditGroupModal isOpen group={group} onClose={closeModal} />}
			{modalState?.type === 'delete' && (
				<DeleteGroupModal isOpen groupId={group.groupId} onClose={closeModal} onDeleted={onDeleted} />
			)}
		</PageLayout>
	);
};

export {AdminGroupDetailPage};
export type {AdminGroupDetailPageProps};
