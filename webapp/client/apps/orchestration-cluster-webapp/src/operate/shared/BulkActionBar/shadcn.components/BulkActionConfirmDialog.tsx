/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ReactNode} from 'react';
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

type Props = {
	open: boolean;
	title: string;
	description: ReactNode;
	confirmLabel: string;
	cancelLabel: string;
	isDestructive?: boolean;
	isConfirmDisabled?: boolean;
	onOpenChange: (open: boolean) => void;
	onCancel: () => void;
	onConfirm: () => void;
};

const BulkActionConfirmDialog: React.FC<Props> = ({
	open,
	title,
	description,
	confirmLabel,
	cancelLabel,
	isDestructive = false,
	isConfirmDisabled = false,
	onOpenChange,
	onCancel,
	onConfirm,
}) => (
	<AlertDialog open={open} onOpenChange={onOpenChange}>
		<AlertDialogContent>
			<AlertDialogHeader>
				<AlertDialogTitle>{title}</AlertDialogTitle>
			</AlertDialogHeader>
			<AlertDialogDescription>{description}</AlertDialogDescription>
			<AlertDialogFooter>
				<AlertDialogCancel onClick={onCancel}>{cancelLabel}</AlertDialogCancel>
				<AlertDialogAction
					className={isDestructive ? buttonVariants({variant: 'destructive'}) : undefined}
					disabled={isConfirmDisabled}
					onClick={(event) => {
						event.preventDefault();
						onConfirm();
					}}
				>
					{confirmLabel}
				</AlertDialogAction>
			</AlertDialogFooter>
		</AlertDialogContent>
	</AlertDialog>
);

export {BulkActionConfirmDialog};
