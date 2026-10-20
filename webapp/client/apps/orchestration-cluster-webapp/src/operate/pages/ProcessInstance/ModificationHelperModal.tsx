/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createPortal} from 'react-dom';
import {observer} from 'mobx-react-lite';
import {useTranslation} from 'react-i18next';
import {Checkbox, Modal, Stack} from '@carbon/react';
import {themeStore} from '#/shared/theme/theme';
import diagramLight from '#/operate/assets/modification-helper-diagram-light.svg';
import diagramDark from '#/operate/assets/modification-helper-diagram-dark.svg';
import {setModificationHelperHidden} from './modificationHelperPreference';
import {
	AddIcon,
	CancelIcon,
	HelperDiagram,
	Modification,
	Modifications,
	ModificationType,
	MoveIcon,
} from './processInstanceModification.styled';

type Props = {
	open: boolean;
	onClose: () => void;
	onSubmit: () => void;
};

const ModificationHelperModal: React.FC<Props> = observer(({open, onClose, onSubmit}) => {
	const {t} = useTranslation();

	return createPortal(
		<Modal
			open={open}
			preventCloseOnClickOutside
			modalHeading={t('operate.processInstance.modification.frameTitle')}
			primaryButtonText={t('operate.processes.batchModification.continue')}
			secondaryButtonText={t('operate.processes.toolbar.cancel')}
			onRequestClose={onClose}
			onSecondarySubmit={onClose}
			onRequestSubmit={onSubmit}
			size="md"
		>
			<Stack gap={5}>
				<p>{t('operate.processInstance.modification.helperIntro')}</p>
				<p>{t('operate.processInstance.modification.helperSelect')}</p>
				<Modifications gap={3}>
					<Modification>
						<ModificationType>
							{t('operate.processInstance.modification.add')} <AddIcon />
						</ModificationType>
						{t('operate.processInstance.modification.helperAdd')}
					</Modification>
					<Modification>
						<ModificationType>
							{t('operate.processInstance.modification.cancel')} <CancelIcon />
						</ModificationType>
						{t('operate.processInstance.modification.helperCancel')}
					</Modification>
					<Modification>
						<ModificationType>
							{t('operate.processInstance.modification.move')} <MoveIcon />
						</ModificationType>
						{t('operate.processInstance.modification.helperMove')}
					</Modification>
				</Modifications>
				<p>{t('operate.processInstance.modification.helperVariables')}</p>
				<p>{t('operate.processInstance.modification.helperReview')}</p>
				<HelperDiagram src={themeStore.actualTheme === 'light' ? diagramLight : diagramDark} alt="" />
				<Checkbox
					id="hide-modification-helper"
					labelText={t('operate.processes.batchModification.hideHelper')}
					onChange={(_, {checked}) => setModificationHelperHidden(checked)}
				/>
			</Stack>
		</Modal>,
		document.getElementById('main-content') ?? document.body,
	);
});

export {ModificationHelperModal};
