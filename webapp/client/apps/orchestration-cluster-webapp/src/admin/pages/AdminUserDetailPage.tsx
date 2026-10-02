/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
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
	Label,
	PageHeader,
	PageLayout,
	Text,
} from '@camunda/design-system';
import type {User} from '@camunda/camunda-api-zod-schemas/8.11';
import {EditUserModal} from '#/admin/modules/users/EditUserModal';
import {DeleteUserModal} from '#/admin/modules/users/DeleteUserModal';

type ModalState = {type: 'edit'} | {type: 'delete'} | null;

type AdminUserDetailPageProps = {
	user: User;
	onDeleted: () => void;
};

const AdminUserDetailPage: React.FC<AdminUserDetailPageProps> = ({user, onDeleted}) => {
	const {t} = useTranslation();
	const [modalState, setModalState] = useState<ModalState>(null);
	const closeModal = () => setModalState(null);

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<PageHeader
					title={user.username}
					breadcrumb={
						<Breadcrumb>
							<BreadcrumbList>
								<BreadcrumbItem>
									<BreadcrumbLink href="/admin/users">{t('admin.headerNavItemUsers')}</BreadcrumbLink>
								</BreadcrumbItem>
								<BreadcrumbSeparator />
								<BreadcrumbItem>
									<BreadcrumbPage>{user.username}</BreadcrumbPage>
								</BreadcrumbItem>
							</BreadcrumbList>
						</Breadcrumb>
					}
					actions={
						<>
							<Button type="button" variant="secondary" onClick={() => setModalState({type: 'edit'})}>
								<Pencil aria-hidden />
								{t('admin.users.editUser')}
							</Button>
							<Button type="button" variant="destructive" onClick={() => setModalState({type: 'delete'})}>
								<Trash2 aria-hidden />
								{t('admin.users.deleteUser')}
							</Button>
						</>
					}
				/>

				<div className="grid max-w-md grid-cols-[auto_1fr] gap-x-6 gap-y-4">
					<Label>{t('admin.users.username')}</Label>
					<Text as="p">{user.username}</Text>
					<Label>{t('admin.users.name')}</Label>
					<Text as="p">{user.name || '-'}</Text>
					<Label>{t('admin.users.email')}</Label>
					<Text as="p">{user.email || '-'}</Text>
				</div>
			</div>

			{modalState?.type === 'edit' && <EditUserModal isOpen user={user} onClose={closeModal} />}
			{modalState?.type === 'delete' && (
				<DeleteUserModal isOpen username={user.username} onClose={closeModal} onDeleted={onDeleted} />
			)}
		</PageLayout>
	);
};

export {AdminUserDetailPage};
