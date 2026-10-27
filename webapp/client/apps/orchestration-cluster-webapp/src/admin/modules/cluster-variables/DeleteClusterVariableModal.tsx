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
import type {ClusterVariable} from '@camunda/camunda-api-zod-schemas/8.11';
import {useClusterVariableMutations} from './useClusterVariableMutations';

type Props = {
	clusterVariable: ClusterVariable | null;
	onClose: () => void;
};

const DeleteClusterVariableModal: React.FC<Props> = ({clusterVariable, onClose}) => {
	const {t} = useTranslation();
	const {remove} = useClusterVariableMutations();
	const isOpen = clusterVariable !== null;

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
			if (clusterVariable === null) {
				return;
			}
			remove.mutate(clusterVariable, {onSuccess: onClose});
		},
		[clusterVariable, onClose, remove],
	);

	return (
		<AlertDialog open={isOpen} onOpenChange={handleOpenChange}>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>
						{isOpen ? t('admin.clusterVariables.deleteClusterVariableModalTitle') : undefined}
					</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans
						i18nKey="admin.clusterVariables.deleteClusterVariableConfirmation"
						values={{name: clusterVariable?.name}}
						components={{strong: <strong />}}
					/>
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel disabled={remove.isPending}>{t('admin.clusterVariables.cancelButton')}</AlertDialogCancel>
					<AlertDialogAction
						className={buttonVariants({variant: 'destructive'})}
						disabled={remove.isPending}
						onClick={handleDelete}
					>
						{t('admin.clusterVariables.deleteButton')}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {DeleteClusterVariableModal};
