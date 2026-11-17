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
import {useUserMutations} from './useUserMutations';

type Props = {
	isOpen: boolean;
	username: string;
	onClose: () => void;
	onDeleted?: () => void;
};

const DeleteUserModal: React.FC<Props> = ({isOpen, username, onClose, onDeleted}) => {
	const {t} = useTranslation();
	const {remove} = useUserMutations();

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
			remove.mutate(username, {
				onSuccess: () => {
					onDeleted?.();
					onClose();
				},
			});
		},
		[onClose, onDeleted, remove, username],
	);

	return (
		<AlertDialog open={isOpen} onOpenChange={handleOpenChange}>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>{isOpen ? t('admin.users.deleteUser') : undefined}</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans i18nKey="admin.users.confirmDeleteUser" values={{username}} components={{strong: <strong />}} />{' '}
					{t('admin.users.actionCannotBeUndone')}
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel disabled={remove.isPending}>{t('admin.users.cancel')}</AlertDialogCancel>
					<AlertDialogAction
						className={buttonVariants({variant: 'destructive'})}
						disabled={remove.isPending}
						onClick={handleDelete}
					>
						{t('admin.users.deleteUser')}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {DeleteUserModal};
