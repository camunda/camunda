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
import type {TenantTab} from './detailSearchSchema';
import {useTenantMemberMutations} from './useTenantMemberMutations';

type Props = {
	tab: TenantTab;
	tenantId: string;
	memberId: string | null;
	onClose: () => void;
};

const UnassignMemberModal: React.FC<Props> = ({tab, tenantId, memberId, onClose}) => {
	const {t} = useTranslation();
	const {unassign} = useTenantMemberMutations(tab, tenantId);
	const isOpen = memberId !== null;

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
					<AlertDialogTitle>{isOpen ? t('admin.tenants.unassignModalTitle') : undefined}</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans
						i18nKey="admin.tenants.unassignConfirmation"
						values={{id: memberId, tenantId}}
						components={{strong: <strong />}}
					/>
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel disabled={unassign.isPending}>{t('admin.tenants.cancelButton')}</AlertDialogCancel>
					<AlertDialogAction
						className={buttonVariants({variant: 'destructive'})}
						disabled={unassign.isPending}
						onClick={(event) => {
							event.preventDefault();
							if (memberId !== null) {
								unassign.mutate(memberId, {onSuccess: onClose});
							}
						}}
					>
						{t('admin.tenants.unassignButton')}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {UnassignMemberModal};
