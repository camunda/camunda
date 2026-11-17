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
import type {GlobalTaskListener} from '@camunda/camunda-api-zod-schemas/8.11';
import {useGlobalTaskListenerMutations} from './useGlobalTaskListenerMutations';

type Props = {
	globalTaskListener: GlobalTaskListener | null;
	onClose: () => void;
};

const DeleteGlobalTaskListenerModal: React.FC<Props> = ({globalTaskListener, onClose}) => {
	const {t} = useTranslation();
	const {remove} = useGlobalTaskListenerMutations();
	const isOpen = globalTaskListener !== null;

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
			if (globalTaskListener === null) {
				return;
			}
			remove.mutate({id: globalTaskListener.id}, {onSuccess: onClose});
		},
		[globalTaskListener, onClose, remove],
	);

	return (
		<AlertDialog open={isOpen} onOpenChange={handleOpenChange}>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>
						{isOpen ? t('admin.globalTaskListeners.deleteGlobalTaskListenerModalTitle') : undefined}
					</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans
						i18nKey="admin.globalTaskListeners.deleteGlobalTaskListenerConfirmation"
						values={{id: globalTaskListener?.id}}
						components={{strong: <strong />}}
					/>
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel disabled={remove.isPending}>
						{t('admin.globalTaskListeners.cancelButton')}
					</AlertDialogCancel>
					<AlertDialogAction
						className={buttonVariants({variant: 'destructive'})}
						disabled={remove.isPending}
						onClick={handleDelete}
					>
						{t('admin.globalTaskListeners.deleteButton')}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {DeleteGlobalTaskListenerModal};
