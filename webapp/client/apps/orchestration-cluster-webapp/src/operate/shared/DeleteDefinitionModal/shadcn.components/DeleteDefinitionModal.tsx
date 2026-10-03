/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
	Alert,
	Button,
	Checkbox,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Label,
} from '@camunda/design-system';

type Props = {
	isVisible: boolean;
	title: string;
	description: string;
	bodyContent: React.ReactNode;
	confirmationText: string;
	warningTitle?: string;
	warningContent?: React.ReactNode;
	onClose: () => void;
	onDelete: () => void;
};

const DeleteDefinitionModal: React.FC<Props> = ({
	isVisible,
	title,
	description,
	bodyContent,
	warningTitle,
	confirmationText,
	warningContent,
	onClose,
	onDelete,
}) => {
	const {t} = useTranslation();
	const confirmationCheckboxId = useId();
	const confirmationErrorId = useId();
	const [isConfirmed, setIsConfirmed] = useState(false);
	const [hasConfirmationError, setHasConfirmationError] = useState(false);

	const resetState = () => {
		setHasConfirmationError(false);
		setIsConfirmed(false);
	};

	const handleClose = () => {
		resetState();
		onClose();
	};

	const handleDelete = () => {
		if (!isConfirmed) {
			setHasConfirmationError(true);
			return;
		}

		onDelete();
		resetState();
	};

	return (
		<Dialog
			open={isVisible}
			onOpenChange={(open) => {
				if (!open) {
					handleClose();
				}
			}}
		>
			<DialogContent size="md" aria-describedby={undefined} onInteractOutside={(event) => event.preventDefault()}>
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
				</DialogHeader>
				<DialogBody>
					<div className="space-y-6">
						<p className="text-sm leading-5 text-foreground">{description}</p>
						{bodyContent}
						{warningContent !== undefined ? (
							<Alert variant="warning" title={warningTitle} description={warningContent} />
						) : null}
						<div className="space-y-2">
							<div className="flex items-start gap-2">
								<Checkbox
									id={confirmationCheckboxId}
									checked={isConfirmed}
									aria-invalid={hasConfirmationError}
									aria-describedby={hasConfirmationError ? confirmationErrorId : undefined}
									onCheckedChange={(checked) => {
										const nextCheckedState = checked === true;

										if (nextCheckedState && hasConfirmationError) {
											setHasConfirmationError(false);
										}

										setIsConfirmed(nextCheckedState);
									}}
								/>
								<Label htmlFor={confirmationCheckboxId} className="cursor-pointer text-sm leading-5 font-normal">
									{confirmationText}
								</Label>
							</div>
							{hasConfirmationError ? (
								<p id={confirmationErrorId} className="text-sm leading-5 text-destructive" role="alert">
									{t('operate.shared.deleteDefinitionModal.confirmationError')}
								</p>
							) : null}
						</div>
					</div>
				</DialogBody>
				<DialogFooter>
					<Button type="button" variant="secondary" onClick={handleClose}>
						{t('operate.shared.deleteDefinitionModal.cancelButton')}
					</Button>
					<Button type="button" variant="destructive" disabled={!isConfirmed} onClick={handleDelete}>
						{t('operate.shared.deleteDefinitionModal.deleteButton')}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
};

export {DeleteDefinitionModal};
