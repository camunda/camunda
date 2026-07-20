/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Modal, Stack, ActionableNotification, Checkbox} from '@carbon/react';
import {Description, WarningContainer} from './styled';

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
	const [isConfirmed, setIsConfirmed] = useState(false);
	const [hasConfirmationError, setHasConfirmationError] = useState(false);

	return (
		<Modal
			open={isVisible}
			danger
			preventCloseOnClickOutside
			modalHeading={title}
			primaryButtonText={t('operate.shared.deleteDefinitionModal.deleteButton')}
			secondaryButtonText={t('operate.shared.deleteDefinitionModal.cancelButton')}
			onRequestSubmit={() => {
				if (!isConfirmed) {
					setHasConfirmationError(true);
					return;
				}

				onDelete();
				setIsConfirmed(false);
			}}
			onRequestClose={() => {
				setHasConfirmationError(false);
				onClose();
				setIsConfirmed(false);
			}}
			size="md"
		>
			<Stack gap={6}>
				<Description>{description}</Description>
				{bodyContent}
				{warningContent && (
					<ActionableNotification
						kind="warning"
						inline
						hideCloseButton
						lowContrast
						title={warningTitle}
						children={<WarningContainer>{warningContent}</WarningContainer>}
						actionButtonLabel=""
					/>
				)}
				<Checkbox
					checked={isConfirmed}
					id={confirmationCheckboxId}
					labelText={confirmationText}
					invalid={hasConfirmationError}
					invalidText={t('operate.shared.deleteDefinitionModal.confirmationError')}
					warnText=""
					onChange={(_, {checked}) => {
						if (checked && hasConfirmationError) {
							setHasConfirmationError(false);
						}

						setIsConfirmed(checked);
					}}
				/>
			</Stack>
		</Modal>
	);
};

export {DeleteDefinitionModal};
