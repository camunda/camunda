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
import {useGroupMutations} from './useGroupMutations';

type Props = {
	isOpen: boolean;
	groupId: string;
	onClose: () => void;
	onDeleted?: () => void;
};

const DeleteGroupModal: React.FC<Props> = ({isOpen, groupId, onClose, onDeleted}) => {
	const {t} = useTranslation();
	const {remove} = useGroupMutations();

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
			remove.mutate(groupId, {
				onSuccess: () => {
					onDeleted?.();
					onClose();
				},
			});
		},
		[onClose, onDeleted, remove, groupId],
	);

	return (
		<AlertDialog open={isOpen} onOpenChange={handleOpenChange}>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>{isOpen ? t('admin.groups.deleteGroup') : undefined}</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans i18nKey="admin.groups.confirmDeleteGroup" values={{groupId}} components={{strong: <strong />}} />{' '}
					{t('admin.groups.actionCannotBeUndone')}
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel disabled={remove.isPending}>{t('admin.groups.cancel')}</AlertDialogCancel>
					<AlertDialogAction
						className={buttonVariants({variant: 'destructive'})}
						disabled={remove.isPending}
						onClick={handleDelete}
					>
						{t('admin.groups.deleteGroup')}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {DeleteGroupModal};
