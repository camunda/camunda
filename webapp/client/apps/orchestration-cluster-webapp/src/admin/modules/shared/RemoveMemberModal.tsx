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
	buttonVariants,
} from '@camunda/design-system';

type Props = {
	isOpen: boolean;
	owner: 'groups' | 'roles' | 'tenants';
	kind: string;
	memberId: string;
	isPending: boolean;
	onRemove: () => void;
	onClose: () => void;
};

const RemoveMemberModal: React.FC<Props> = ({isOpen, owner, kind, memberId, isPending, onRemove, onClose}) => {
	const {t} = useTranslation();
	const remove = t(`admin.${owner}.members.${kind}.remove`);

	const handleRemove = (event: React.MouseEvent<HTMLButtonElement>) => {
		event.preventDefault();
		onRemove();
	};

	return (
		<AlertDialog
			open={isOpen}
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>{isOpen ? remove : undefined}</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans
						i18nKey={`admin.${owner}.members.${kind}.confirmRemove`}
						values={{id: memberId}}
						components={{strong: <strong />}}
					/>
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel disabled={isPending}>{t(`admin.${owner}.cancel`)}</AlertDialogCancel>
					<AlertDialogAction
						className={buttonVariants({variant: 'destructive'})}
						disabled={isPending}
						onClick={handleRemove}
					>
						{remove}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {RemoveMemberModal};
