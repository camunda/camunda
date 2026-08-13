/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// FLAG: `FeatureFlags` is a Carbon-only provider with no carbon-compat entry and
// no DS equivalent. It is used here solely to opt Carbon's modal into
// `enableFocusWrapWithoutSentinels`, which is a fix for Carbon's own focus-sentinel
// markup — the DS Dialog (Radix) does not use sentinels and needs no equivalent.
// It therefore stays on @carbon/react and wraps the flag-off path only.
import {FeatureFlags} from '@carbon/react';
import {Modal} from '#/shared/design-system-compat';
import {featureFlags} from '#/shared/feature-flags';
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

	// The compat Modal drops `preventCloseOnClickOutside` and `size` (its own
	// warnDroppedProps). Both are compensated on the DS path: without the guard a
	// backdrop click would fire onRequestClose and navigate the user off the
	// Processes page, and without `size="md"` the dialog is too narrow for the
	// 404px illustration. Same approach as JSONEditorModal.tsx; both gaps are
	// logged in docs/migration/human-follow-up.md.
	const dsModalProps = featureFlags.dsTasklistUI
		? ({
				onInteractOutside: (event: Event) => {
					event.preventDefault();
				},
				className: styles.modal,
			} as Partial<React.ComponentProps<typeof Modal>>)
		: {};

	const modal = (
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
			{...dsModalProps}
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
	);

	return (
		<>
			{featureFlags.dsTasklistUI ? modal : <FeatureFlags enableFocusWrapWithoutSentinels>{modal}</FeatureFlags>}
			{hasConsented ? children : null}
		</>
	);
}

export {FirstTimeProcessWarning};
