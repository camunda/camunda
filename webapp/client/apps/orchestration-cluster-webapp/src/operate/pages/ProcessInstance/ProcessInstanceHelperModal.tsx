/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {observer} from 'mobx-react-lite';
import {Trans, useTranslation} from 'react-i18next';
import {Modal} from '@carbon/react';
import {themeStore} from '#/shared/theme/theme';
import previewLight from '#/operate/assets/process-instance-details-light.png';
import previewDark from '#/operate/assets/process-instance-details-dark.png';
import {hideProcessInstanceHelper, isProcessInstanceHelperHidden} from './processInstanceHelperPreference';
import {ChangesList, PreviewImage} from './processInstanceHelper.styled';

const ProcessInstanceHelperModal: React.FC = observer(() => {
	const {t} = useTranslation();
	const [isOpen, setIsOpen] = useState(() => !isProcessInstanceHelperHidden());

	if (!isOpen) {
		return null;
	}

	const close = () => {
		hideProcessInstanceHelper();
		setIsOpen(false);
	};

	return (
		<Modal
			open
			preventCloseOnClickOutside
			modalHeading={t('operate.processInstance.helper.title')}
			primaryButtonText={t('operate.processInstance.helper.gotIt')}
			onRequestSubmit={close}
			onRequestClose={close}
			size="md"
		>
			<p>{t('operate.processInstance.helper.intro')}</p>
			<ChangesList>
				{(['incidents', 'details', 'callActivity'] as const).map((change) => (
					<li key={change}>
						<Trans i18nKey={`operate.processInstance.helper.${change}`} components={{strong: <strong />}} />
					</li>
				))}
			</ChangesList>
			<PreviewImage
				src={themeStore.actualTheme === 'light' ? previewLight : previewDark}
				alt={t('operate.processInstance.helper.imageAlt')}
			/>
		</Modal>
	);
});

export {ProcessInstanceHelperModal};
