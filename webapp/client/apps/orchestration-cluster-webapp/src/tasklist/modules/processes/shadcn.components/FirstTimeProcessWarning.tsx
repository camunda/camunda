/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';
import {useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from '@camunda/design-system';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import Illustration from '#/tasklist/modules/processes/first-time-process-warning.svg';

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
		navigate({to: '/shadcn/tasklist', replace: true});
	}, [navigate]);
	const handleConsent = useCallback(() => {
		storeStateLocally('tasklist.hasConsentedToStartProcess', true);
		setHasConsented(true);
	}, []);

	return (
		<>
			<Dialog
				open={!hasConsented}
				onOpenChange={(open) => {
					if (!open) {
						leaveProcesses();
					}
				}}
			>
				<DialogContent size="md" onInteractOutside={(event) => event.preventDefault()}>
					<DialogHeader>
						<DialogTitle>{t('tasklist.processesFirstTimeModalHeading')}</DialogTitle>
					</DialogHeader>
					<DialogBody className="flex flex-col items-center text-center">
						<img className="mb-8 h-auto w-full max-w-[404px]" src={Illustration} alt="" />
						<div>
							<DialogDescription>{t('tasklist.processesFirstTimeModalBodyPart1')}</DialogDescription>
							<p>{t('tasklist.processesFirstTimeModalBodyPart2')}</p>
							<p>{t('tasklist.processesFirstTimeModalBodyPart3')}</p>
						</div>
					</DialogBody>
					<DialogFooter>
						<Button type="button" variant="secondary" onClick={leaveProcesses}>
							{t('tasklist.processesFirstTimeModalCancelButtonLabel')}
						</Button>
						<Button type="button" onClick={handleConsent}>
							{t('tasklist.processesFirstTimeModalContinueButtonLabel')}
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
			{hasConsented ? children : null}
		</>
	);
}

export {FirstTimeProcessWarning};
