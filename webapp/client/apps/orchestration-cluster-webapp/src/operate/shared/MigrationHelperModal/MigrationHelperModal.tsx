/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import {Trans, useTranslation} from 'react-i18next';
import {Checkbox, Link, Modal, Stack} from '@carbon/react';
import {MigrationHelperList, MigrationHelperListItem} from './styled';
import {setMigrationHelperHidden} from './migrationHelperPreference';

type Props = {
	open: boolean;
	onClose: () => void;
	onSubmit: () => void;
};

function MigrationHelperModal({open, onClose, onSubmit}: Props) {
	const {t} = useTranslation();

	return createPortal(
		<Modal
			open={open}
			preventCloseOnClickOutside
			modalHeading={t('operate.processes.migration.helperTitle')}
			primaryButtonText={t('operate.processes.batchModification.continue')}
			secondaryButtonText={t('operate.processes.toolbar.cancel')}
			onRequestClose={onClose}
			onSecondarySubmit={onClose}
			onRequestSubmit={onSubmit}
			size="md"
		>
			<Stack gap={5}>
				<MigrationHelperList nested>
					<MigrationHelperListItem>{t('operate.processes.migration.helperPurpose')}</MigrationHelperListItem>
					<MigrationHelperListItem>{t('operate.processes.migration.helperImpact')}</MigrationHelperListItem>
					<MigrationHelperListItem>{t('operate.processes.migration.helperPlanning')}</MigrationHelperListItem>
				</MigrationHelperList>
				<p>
					<Trans
						i18nKey="operate.processes.migration.helperDocumentation"
						components={{
							docs: (
								<Link
									href="https://docs.camunda.io/docs/components/operate/userguide/process-instance-migration/"
									target="_blank"
									inline
								/>
							),
						}}
					/>
				</p>
				<Checkbox
					id="hide-migration-helper"
					labelText={t('operate.processes.batchModification.hideHelper')}
					onChange={(_, {checked}) => setMigrationHelperHidden(checked)}
				/>
			</Stack>
		</Modal>,
		document.getElementById('main-content') ?? document.body,
	);
}

export {MigrationHelperModal};
