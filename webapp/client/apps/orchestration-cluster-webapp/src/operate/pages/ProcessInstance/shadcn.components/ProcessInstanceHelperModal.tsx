/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {observer} from 'mobx-react-lite';
import {Trans, useTranslation} from 'react-i18next';
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
import {themeStore} from '#/shared/theme/theme';
import previewLight from '#/operate/assets/process-instance-details-ds-light.png';
import previewDark from '#/operate/assets/process-instance-details-ds-dark.png';
import {hideProcessInstanceHelper, isProcessInstanceHelperHidden} from '../processInstanceHelperPreference';

const ProcessInstanceHelperModal: React.FC = observer(() => {
	const {t} = useTranslation();
	const [isOpen, setIsOpen] = useState(() => !isProcessInstanceHelperHidden());
	const gotItButtonRef = useRef<HTMLButtonElement>(null);

	if (!isOpen) {
		return null;
	}

	const close = () => {
		hideProcessInstanceHelper();
		setIsOpen(false);
	};

	return (
		<Dialog
			open
			onOpenChange={(nextOpen) => {
				if (!nextOpen) {
					close();
				}
			}}
		>
			<DialogContent size="lg" initialFocusRef={gotItButtonRef} dismissOnOutsideClick={false}>
				<DialogHeader>
					<DialogTitle>{t('operate.processInstance.helper.title')}</DialogTitle>
					<DialogDescription>{t('operate.processInstance.helper.intro')}</DialogDescription>
				</DialogHeader>
				<DialogBody className="flex flex-col gap-4 text-sm">
					<ul className="ml-4 flex list-disc flex-col gap-2">
						{(['incidents', 'details', 'callActivity'] as const).map((change) => (
							<li key={change}>
								<Trans i18nKey={`operate.processInstance.helper.${change}`} components={{strong: <strong />}} />
							</li>
						))}
					</ul>
					<img
						src={themeStore.actualTheme === 'light' ? previewLight : previewDark}
						alt={t('operate.processInstance.helper.imageAlt')}
						className="h-auto w-full"
					/>
				</DialogBody>
				<DialogFooter>
					<Button ref={gotItButtonRef} onClick={close}>
						{t('operate.processInstance.helper.gotIt')}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
});

export {ProcessInstanceHelperModal};
