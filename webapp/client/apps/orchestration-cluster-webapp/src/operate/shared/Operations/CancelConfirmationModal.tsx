/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Modal, Link} from '@carbon/react';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {useCallHierarchy} from './Operations.queries';

type Props = {
	processInstanceKey: ProcessInstance['processInstanceKey'];
	open: boolean;
	onConfirm: () => void;
	onCancel: () => void;
};

const CancelConfirmationModal: React.FC<Props> = ({processInstanceKey, open, onConfirm, onCancel}) => {
	const {t} = useTranslation();
	const {data: callHierarchy} = useCallHierarchy(processInstanceKey, {enabled: open});
	const rootInstanceId = callHierarchy?.[0]?.processInstanceKey;

	if (rootInstanceId) {
		return (
			<Modal
				open={open}
				preventCloseOnClickOutside
				modalHeading={t('operate.shared.operations.cancelRootInstanceModal.heading')}
				passiveModal
				onRequestClose={onCancel}
				size="md"
				data-testid="passive-cancellation-modal"
			>
				<p>
					{t('operate.shared.operations.cancelRootInstanceModal.bodyBeforeLink')}{' '}
					<Link
						href={`/operate/processes/${rootInstanceId}`}
						title={t('operate.shared.operations.cancelRootInstanceModal.linkTitle', {rootInstanceId})}
					>
						{rootInstanceId}
					</Link>{' '}
					{t('operate.shared.operations.cancelRootInstanceModal.bodyAfterLink')}
				</p>
			</Modal>
		);
	}

	return (
		<Modal
			open={open}
			preventCloseOnClickOutside
			modalHeading={t('operate.shared.operations.cancelConfirmationModal.heading')}
			primaryButtonText={t('operate.shared.operations.cancelConfirmationModal.applyButton')}
			secondaryButtonText={t('operate.shared.operations.cancelConfirmationModal.cancelButton')}
			onRequestSubmit={onConfirm}
			onRequestClose={onCancel}
			size="md"
			data-testid="confirm-cancellation-modal"
		>
			<p>{t('operate.shared.operations.cancelConfirmationModal.body', {processInstanceKey})}</p>
			<p>{t('operate.shared.operations.cancelConfirmationModal.hint')}</p>
		</Modal>
	);
};

export {CancelConfirmationModal};
