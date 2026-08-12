/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FeatureFlags, Modal} from '@carbon/react';
import {useNavigate} from '@tanstack/react-router';
import {useCallback, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import Illustration from '#/tasklist/modules/processes/first-time-process-warning.svg';
import styles from './FirstTimeProcessWarning.module.scss';

type Props = {
	children?: React.ReactNode;
};

function FirstTimeProcessWarning({children}: Props) {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const [hasConsented, setHasConsented] = useState(
		() => getStateLocally('tasklist.hasConsentedToStartProcess') === true,
	);
	const leaveProcesses = useCallback(() => {
		navigate({to: '/tasklist', replace: true});
	}, [navigate]);
	const handleRequestSubmit = useCallback(() => {
		storeStateLocally('tasklist.hasConsentedToStartProcess', true);
		setHasConsented(true);
	}, []);

	return (
		<>
			<FeatureFlags enableFocusWrapWithoutSentinels>
				<Modal
					aria-label={t('tasklist.processesFirstTimeModalAriaLabel')}
					modalHeading={t('tasklist.processesFirstTimeModalHeading')}
					secondaryButtonText={t('tasklist.processesFirstTimeModalCancelButtonLabel')}
					primaryButtonText={t('tasklist.processesFirstTimeModalContinueButtonLabel')}
					open={!hasConsented}
					onRequestClose={leaveProcesses}
					onSecondarySubmit={leaveProcesses}
					onRequestSubmit={handleRequestSubmit}
					preventCloseOnClickOutside
					size="md"
				>
					<div className={styles.content}>
						<img className={styles.image} src={Illustration} alt="" />
						<div>
							<p>{t('tasklist.processesFirstTimeModalBodyPart1')}</p>
							<p>{t('tasklist.processesFirstTimeModalBodyPart2')}</p>
							<br />
							<p>{t('tasklist.processesFirstTimeModalBodyPart3')}</p>
						</div>
					</div>
				</Modal>
			</FeatureFlags>
			{hasConsented ? children : null}
		</>
	);
}

export {FirstTimeProcessWarning};
