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
import type {Authorization} from '@camunda/camunda-api-zod-schemas/8.11';
import {useAuthorizationMutations} from './useAuthorizationMutations';

type Props = {
	authorization: Authorization | null;
	onClose: () => void;
};

const DeleteAuthorizationModal: React.FC<Props> = ({authorization, onClose}) => {
	const {t} = useTranslation();
	const {remove} = useAuthorizationMutations();
	const isOpen = authorization !== null;

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
			if (authorization === null) {
				return;
			}
			remove.mutate({authorizationKey: authorization.authorizationKey}, {onSuccess: onClose});
		},
		[authorization, onClose, remove],
	);

	return (
		<AlertDialog open={isOpen} onOpenChange={handleOpenChange}>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>
						{isOpen ? t('admin.authorizations.deleteAuthorizationModalTitle') : undefined}
					</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans
						i18nKey="admin.authorizations.deleteAuthorizationConfirmation"
						values={{ownerId: authorization?.ownerId, resourceType: authorization?.resourceType}}
						components={{strong: <strong />}}
					/>
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel disabled={remove.isPending}>{t('admin.authorizations.cancelButton')}</AlertDialogCancel>
					<AlertDialogAction
						className={buttonVariants({variant: 'destructive'})}
						disabled={remove.isPending}
						onClick={handleDelete}
					>
						{t('admin.authorizations.deleteButton')}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {DeleteAuthorizationModal};
