/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	Alert,
	AlertDialog,
	AlertDialogAction,
	AlertDialogCancel,
	AlertDialogContent,
	AlertDialogDescription,
	AlertDialogFooter,
	AlertDialogHeader,
	AlertDialogTitle,
	Dialog,
	DialogBody,
	DialogContent,
	DialogDescription,
	DialogHeader,
	DialogTitle,
	Link,
} from '@camunda/design-system';
import {LoaderCircle} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {getBootConfig} from '#/shared/config/getBootConfig';
import {mergePathname} from '#/shared/http/mergePathname';
import {useCallHierarchy} from '../Operations.queries';

type Props = {
	processInstanceKey: ProcessInstance['processInstanceKey'];
	open: boolean;
	onConfirm: () => void;
	onCancel: () => void;
};

const CancelConfirmationModal: React.FC<Props> = ({processInstanceKey, open, onConfirm, onCancel}) => {
	const {t} = useTranslation();
	const {data: callHierarchy, isError, isPending} = useCallHierarchy(processInstanceKey, {enabled: open});
	const rootInstanceId = callHierarchy?.[0]?.processInstanceKey;

	if (rootInstanceId) {
		const rootInstancePath = mergePathname(getBootConfig().contextPath, `/operate/processes/${rootInstanceId}`);

		return (
			<Dialog
				open={open}
				onOpenChange={(nextOpen) => {
					if (!nextOpen) {
						onCancel();
					}
				}}
			>
				<DialogContent
					size="md"
					data-testid="passive-cancellation-modal"
					onInteractOutside={(event) => event.preventDefault()}
				>
					<DialogHeader>
						<DialogTitle>{t('operate.shared.operations.cancelRootInstanceModal.heading')}</DialogTitle>
					</DialogHeader>
					<DialogBody>
						<DialogDescription className="text-sm leading-5 text-foreground">
							{t('operate.shared.operations.cancelRootInstanceModal.bodyBeforeLink')}{' '}
							<Link
								href={rootInstancePath}
								title={t('operate.shared.operations.cancelRootInstanceModal.linkTitle', {rootInstanceId})}
							>
								{rootInstanceId}
							</Link>{' '}
							{t('operate.shared.operations.cancelRootInstanceModal.bodyAfterLink')}
						</DialogDescription>
					</DialogBody>
				</DialogContent>
			</Dialog>
		);
	}

	return (
		<AlertDialog
			open={open}
			onOpenChange={(nextOpen) => {
				if (!nextOpen) {
					onCancel();
				}
			}}
		>
			<AlertDialogContent data-testid="confirm-cancellation-modal">
				<AlertDialogHeader>
					<AlertDialogTitle>{t('operate.shared.operations.cancelConfirmationModal.heading')}</AlertDialogTitle>
				</AlertDialogHeader>
				<AlertDialogDescription>
					{t('operate.shared.operations.cancelConfirmationModal.body', {processInstanceKey})}
				</AlertDialogDescription>
				<p>{t('operate.shared.operations.cancelConfirmationModal.hint')}</p>
				{isPending ? (
					<div className="flex flex-row items-center gap-2" role="status" aria-live="polite">
						<LoaderCircle aria-hidden="true" className="size-4 animate-spin" />
						<span>{t('operate.shared.operations.cancelConfirmationModal.verificationLoading')}</span>
					</div>
				) : null}
				{isError ? (
					<Alert
						variant="destructive"
						role="alert"
						title={t('operate.shared.operations.cancelConfirmationModal.verificationError')}
					/>
				) : null}
				<AlertDialogFooter>
					<AlertDialogCancel>{t('operate.shared.operations.cancelConfirmationModal.cancelButton')}</AlertDialogCancel>
					<AlertDialogAction
						disabled={isPending || isError}
						onClick={(event) => {
							// AlertDialogAction auto-closes via onOpenChange(false) on click, which would also
							// invoke `onCancel`. Prevent that default close so confirming and cancelling stay
							// mutually exclusive; `onConfirm` is responsible for closing the modal itself.
							event.preventDefault();
							onConfirm();
						}}
					>
						{t('operate.shared.operations.cancelConfirmationModal.applyButton')}
					</AlertDialogAction>
				</AlertDialogFooter>
			</AlertDialogContent>
		</AlertDialog>
	);
};

export {CancelConfirmationModal};
