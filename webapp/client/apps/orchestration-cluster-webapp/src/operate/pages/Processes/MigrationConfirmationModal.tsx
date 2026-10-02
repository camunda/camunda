/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, type ComponentProps} from 'react';
import {useTranslation} from 'react-i18next';
import {Modal, TextInput} from '@carbon/react';
import {MigrationDetails} from './MigrationDetails';
import {useMigrationInstancesCount} from './useMigrationInstancesCount';

type Props = ComponentProps<typeof MigrationDetails> & {
	hasElementMapping: boolean;
	onClose: () => void;
	onSubmit: () => void;
};

function MigrationConfirmationModal({hasElementMapping, onClose, onSubmit, ...details}: Props) {
	const {t} = useTranslation();
	const [inputValue, setInputValue] = useState('');
	const {status} = useMigrationInstancesCount(details.scope.filter);
	const isDisabled = inputValue !== 'MIGRATE' || !hasElementMapping || status !== 'success';

	return (
		<Modal
			primaryButtonDisabled={isDisabled}
			modalHeading={t('operate.processes.migration.confirmationTitle')}
			size="sm"
			primaryButtonText={t('operate.processes.migration.confirm')}
			secondaryButtonText={t('operate.processes.toolbar.cancel')}
			open
			onRequestClose={onClose}
			preventCloseOnClickOutside
			onRequestSubmit={onSubmit}
			shouldSubmitOnEnter={!isDisabled}
		>
			<MigrationDetails {...details} />
			<TextInput
				autoFocus
				id="migration-confirmation"
				labelText={t('operate.processes.migration.confirmationLabel')}
				onChange={({target}) => setInputValue(target.value)}
				value={inputValue}
				invalid={!['MIGRATE', ''].includes(inputValue)}
				invalidText={t('operate.processes.migration.confirmationInvalid')}
			/>
		</Modal>
	);
}

export {MigrationConfirmationModal};
