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
import type {MemberKind} from './members';
import {useRoleMemberMutations} from './useRoleMemberMutations';

type Props = {
	isOpen: boolean;
	roleId: string;
	kind: MemberKind;
	memberId: string;
	onClose: () => void;
};

const RemoveMemberModal: React.FC<Props> = ({isOpen, roleId, kind, memberId, onClose}) => {
	const {t} = useTranslation();
	const {unassign} = useRoleMemberMutations(roleId);

	const handleRemove = (event: React.MouseEvent<HTMLButtonElement>) => {
		event.preventDefault();
		unassign.mutate({kind, id: memberId}, {onSuccess: onClose});
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
					<AlertDialogTitle>{isOpen ? t(`admin.roles.members.${kind}.remove`) : undefined}</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans
						i18nKey={`admin.roles.members.${kind}.confirmRemove`}
						values={{id: memberId}}
						components={{strong: <strong />}}
					/>
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel disabled={unassign.isPending}>{t('admin.roles.cancel')}</AlertDialogCancel>
					<AlertDialogAction
						className={buttonVariants({variant: 'destructive'})}
						disabled={unassign.isPending}
						onClick={handleRemove}
					>
						{t(`admin.roles.members.${kind}.remove`)}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {RemoveMemberModal};
