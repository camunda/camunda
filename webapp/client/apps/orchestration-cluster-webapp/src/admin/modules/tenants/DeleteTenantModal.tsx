/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';
import {Trans, useTranslation} from 'react-i18next';
import {
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	buttonVariants,
} from '@camunda/design-system';
import type {Tenant} from '@camunda/camunda-api-zod-schemas/8.11';
import {useTenantMutations} from './useTenantMutations';

type Props = {
	tenant: Tenant | null;
	onClose: () => void;
	onDeleted?: () => void;
};

const DeleteTenantModal: React.FC<Props> = ({tenant, onClose, onDeleted}) => {
	const {t} = useTranslation();
	const {remove} = useTenantMutations();
	const isOpen = tenant !== null;

	const handleOpenChange = useCallback(
		(open: boolean) => {
			if (!open) {
				onClose();
			}
		},
		[onClose],
	);

	const handleDelete = useCallback(
		(event: React.MouseEvent<HTMLButtonElement, MouseEvent>) => {
			event.preventDefault();
			if (tenant === null) {
				return;
			}
			remove.mutate(
				{tenantId: tenant.tenantId},
				{
					onSuccess: () => {
						onClose();
						onDeleted?.();
					},
				},
			);
		},
		[onClose, onDeleted, remove, tenant],
	);

	return (
		<AlertDialog open={isOpen} onOpenChange={handleOpenChange}>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>{isOpen ? t('admin.tenants.deleteTenantModalTitle') : undefined}</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans
						i18nKey="admin.tenants.deleteTenantConfirmation"
						values={{name: tenant?.name}}
						components={{strong: <strong />}}
					/>
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel disabled={remove.isPending}>{t('admin.tenants.cancelButton')}</AlertDialogCancel>
					<AlertDialogAction
						className={buttonVariants({variant: 'destructive'})}
						disabled={remove.isPending}
						onClick={handleDelete}
					>
						{t('admin.tenants.deleteButton')}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {DeleteTenantModal};
