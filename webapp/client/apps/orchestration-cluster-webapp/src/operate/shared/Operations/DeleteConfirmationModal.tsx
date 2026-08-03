/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Modal} from '@carbon/react';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';

type Props = {
	processInstanceKey: ProcessInstance['processInstanceKey'];
	open: boolean;
	onConfirm: () => void;
	onCancel: () => void;
};

const DeleteConfirmationModal: React.FC<Props> = ({processInstanceKey, open, onConfirm, onCancel}) => {
	const {t} = useTranslation();

	return (
		<Modal
			open={open}
			danger
			preventCloseOnClickOutside
			modalHeading={t('operate.shared.operations.deleteConfirmationModal.heading')}
			primaryButtonText={t('operate.shared.operations.deleteConfirmationModal.deleteButton')}
			secondaryButtonText={t('operate.shared.operations.deleteConfirmationModal.cancelButton')}
			onRequestSubmit={onConfirm}
			onRequestClose={onCancel}
			size="md"
			data-testid="confirm-deletion-modal"
		>
			<p>{t('operate.shared.operations.deleteConfirmationModal.body', {processInstanceKey})}</p>
			<p>{t('operate.shared.operations.deleteConfirmationModal.hint')}</p>
		</Modal>
	);
};

export {DeleteConfirmationModal};
