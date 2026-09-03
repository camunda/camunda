/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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
	Button,
} from '@camunda/design-system';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {useCallback} from 'react';

type Props = {
	isOpen: boolean;
	filterId: string;
	onClose: () => void;
	onDelete: (filterId: string) => void;
	'data-testid'?: string;
};

const DeleteFilterModal: React.FC<Props> = ({isOpen, onClose, onDelete, filterId, ...props}) => {
	const {t} = useTranslation();
	const filterName = getStateLocally('tasklist.customFilters')?.[filterId]?.name;
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
			onDelete(filterId);
		},
		[filterId, onDelete],
	);

	return (
		<AlertDialog open={isOpen} onOpenChange={handleOpenChange}>
			<AlertDialogContent {...props}>
				<AlertDialogHeader>
					<AlertDialogTitle>{isOpen ? t('tasklist.customFiltersModalDeleteModalHeading') : undefined}</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans
						i18nKey="tasklist.customFiltersModalDeleteModalBody"
						values={{name: filterName}}
						components={{strong: <strong />}}
					/>
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel>{t('tasklist.tasksFiltersModalCancelButtonLabel')}</AlertDialogCancel>
					<AlertDialogAction asChild>
						<Button type="button" variant="destructive" onClick={handleDelete}>
							{t('tasklist.customFiltersModalConfirmDeletionButton')}
						</Button>
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {DeleteFilterModal};
