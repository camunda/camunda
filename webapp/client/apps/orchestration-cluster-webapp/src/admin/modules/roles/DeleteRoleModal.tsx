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
import {useRoleMutations} from './useRoleMutations';

type Props = {
	isOpen: boolean;
	roleId: string;
	onClose: () => void;
	onDeleted?: () => void;
};

const DeleteRoleModal: React.FC<Props> = ({isOpen, roleId, onClose, onDeleted}) => {
	const {t} = useTranslation();
	const {remove} = useRoleMutations();

	const handleOpenChange = useCallback(
		(open: boolean) => {
			if (!open) {
				onClose();
			}
		},
		[onClose],
	);

	const handleDelete = useCallback(
		(event: React.MouseEvent<HTMLButtonElement>) => {
			event.preventDefault();
			remove.mutate(roleId, {
				onSuccess: () => {
					onDeleted?.();
					onClose();
				},
			});
		},
		[onClose, onDeleted, remove, roleId],
	);

	return (
		<AlertDialog open={isOpen} onOpenChange={handleOpenChange}>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>{isOpen ? t('admin.roles.deleteRole') : undefined}</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans i18nKey="admin.roles.confirmDeleteRole" values={{roleId}} components={{strong: <strong />}} />{' '}
					{t('admin.roles.actionCannotBeUndone')}
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel disabled={remove.isPending}>{t('admin.roles.cancel')}</AlertDialogCancel>
					<AlertDialogAction
						className={buttonVariants({variant: 'destructive'})}
						disabled={remove.isPending}
						onClick={handleDelete}
					>
						{t('admin.roles.deleteRole')}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {DeleteRoleModal};
