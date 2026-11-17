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
	DialogDescription,
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
	onClose: () => void;
	onDelete: () => void;
} & ({warningTitle?: never; warningContent?: never} | {warningTitle: string; warningContent: React.ReactNode});

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
	const [isConfirmed, setIsConfirmed] = useState(false);

	const handleClose = () => {
		setIsConfirmed(false);
		onClose();
	};

	const handleDelete = () => {
		onDelete();
		setIsConfirmed(false);
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
			<DialogContent size="md" onInteractOutside={(event) => event.preventDefault()}>
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
				</DialogHeader>
				<DialogBody>
					<div className="space-y-6">
						<DialogDescription className="text-sm leading-5 text-foreground">{description}</DialogDescription>
						{bodyContent}
						{warningContent !== undefined ? (
							<Alert variant="warning" title={warningTitle} description={warningContent} />
						) : null}
						<div className="space-y-2">
							{/* Checkbox nested inside Label so the whole row, including the gap between them, toggles it */}
							<Label
								htmlFor={confirmationCheckboxId}
								className="cursor-pointer items-start text-sm leading-5 font-normal"
							>
								<Checkbox
									id={confirmationCheckboxId}
									checked={isConfirmed}
									onCheckedChange={(checked) => {
										setIsConfirmed(checked === true);
									}}
								/>
								{confirmationText}
							</Label>
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
