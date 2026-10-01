/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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
import {useTranslation} from 'react-i18next';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';

type Props = {
	processInstanceKey: ProcessInstance['processInstanceKey'];
	open: boolean;
	onConfirm: () => void;
	onCancel: () => void;
};

const DeleteConfirmationModal: React.FC<Props> = ({processInstanceKey, open, onConfirm, onCancel}) => {
	const {t} = useTranslation();

	return (
		<AlertDialog
			open={open}
			onOpenChange={(nextOpen) => {
				if (!nextOpen) {
					onCancel();
				}
			}}
		>
			<AlertDialogContent data-testid="confirm-deletion-modal">
				<AlertDialogHeader>
					<AlertDialogTitle>{t('operate.shared.operations.deleteConfirmationModal.heading')}</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					{t('operate.shared.operations.deleteConfirmationModal.body', {processInstanceKey})}
				</AlertDialogDescription>
				<p>{t('operate.shared.operations.deleteConfirmationModal.hint')}</p>
				<AlertDialogFooter>
					<AlertDialogCancel>{t('operate.shared.operations.deleteConfirmationModal.cancelButton')}</AlertDialogCancel>
					<AlertDialogAction className={buttonVariants({variant: 'destructive'})} onClick={onConfirm}>
						{t('operate.shared.operations.deleteConfirmationModal.deleteButton')}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {DeleteConfirmationModal};
