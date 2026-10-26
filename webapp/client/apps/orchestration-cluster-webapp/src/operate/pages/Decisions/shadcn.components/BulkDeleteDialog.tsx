/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {BulkActionConfirmDialog} from '#/operate/shared/BulkActionBar/shadcn.components/BulkActionConfirmDialog';

type BulkDeleteDialogProps = {
	open: boolean;
	selectedCount: number;
	onOpenChange: (open: boolean) => void;
	onCancel: () => void;
	onConfirm: () => void;
};

const BulkDeleteDialog: React.FC<BulkDeleteDialogProps> = ({
	open,
	selectedCount,
	onOpenChange,
	onCancel,
	onConfirm,
}) => {
	const {t} = useTranslation();

	return (
		<BulkActionConfirmDialog
			open={open}
			title={t('operate.decisions.toolbar.deleteModalHeading')}
			description={t('operate.decisions.toolbar.deleteModalBody', {count: selectedCount})}
			confirmLabel={t('operate.decisions.toolbar.delete')}
			cancelLabel={t('operate.decisions.toolbar.deleteModalCancel')}
			isDestructive
			onOpenChange={onOpenChange}
			onCancel={onCancel}
			onConfirm={onConfirm}
		/>
	);
};

export {BulkDeleteDialog};
export type {BulkDeleteDialogProps};
