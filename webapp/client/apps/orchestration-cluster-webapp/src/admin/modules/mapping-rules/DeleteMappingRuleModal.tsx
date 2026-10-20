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
import type {MappingRule} from '@camunda/camunda-api-zod-schemas/8.11';
import {useMappingRuleMutations} from './useMappingRuleMutations';

type Props = {
	mappingRule: MappingRule | null;
	onClose: () => void;
};

const DeleteMappingRuleModal: React.FC<Props> = ({mappingRule, onClose}) => {
	const {t} = useTranslation();
	const {remove} = useMappingRuleMutations();
	const isOpen = mappingRule !== null;

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
			if (mappingRule === null) {
				return;
			}
			remove.mutate({mappingRuleId: mappingRule.mappingRuleId}, {onSuccess: onClose});
		},
		[mappingRule, onClose, remove],
	);

	return (
		<AlertDialog open={isOpen} onOpenChange={handleOpenChange}>
			<AlertDialogContent>
				<AlertDialogHeader>
					<AlertDialogTitle>
						{isOpen ? t('admin.mappingRules.deleteMappingRuleModalTitle') : undefined}
					</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					<Trans
						i18nKey="admin.mappingRules.deleteMappingRuleConfirmation"
						values={{name: mappingRule?.name}}
						components={{strong: <strong />}}
					/>
				</AlertDialogDescription>
				<AlertDialogFooter>
					<AlertDialogCancel disabled={remove.isPending}>{t('admin.mappingRules.cancelButton')}</AlertDialogCancel>
					<AlertDialogAction
						className={buttonVariants({variant: 'destructive'})}
						disabled={remove.isPending}
						onClick={handleDelete}
					>
						{t('admin.mappingRules.deleteButton')}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {DeleteMappingRuleModal};
