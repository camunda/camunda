/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {Button, Modal} from '@carbon/react';
import {BatchModificationActions} from './styled';

type Props = {
	onExit: () => void;
};

function MigrationFooter({onExit}: Props) {
	const {t} = useTranslation();
	const [isExitOpen, setIsExitOpen] = useState(false);

	return (
		<BatchModificationActions orientation="horizontal" gap={5}>
			<Button kind="secondary" size="sm" onClick={() => setIsExitOpen(true)}>
				{t('operate.processes.migration.exit')}
			</Button>
			{createPortal(
				<Modal
					open={isExitOpen}
					danger
					preventCloseOnClickOutside
					modalHeading={t('operate.processes.migration.exitTitle')}
					primaryButtonText={t('operate.processes.migration.exitConfirm')}
					secondaryButtonText={t('operate.processes.toolbar.cancel')}
					onRequestSubmit={() => {
						setIsExitOpen(false);
						onExit();
					}}
					onRequestClose={() => setIsExitOpen(false)}
					size="md"
				>
					<p>{t('operate.processes.migration.exitDiscard')}</p>
					<p>{t('operate.processes.migration.exitProceed')}</p>
				</Modal>,
				document.getElementById('main-content') ?? document.body,
			)}
		</BatchModificationActions>
	);
}

export {MigrationFooter};
