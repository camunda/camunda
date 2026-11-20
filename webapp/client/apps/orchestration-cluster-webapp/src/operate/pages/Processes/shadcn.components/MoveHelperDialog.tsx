/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {useTranslation} from 'react-i18next';
import {
	Button,
	Checkbox,
	Dialog,
	DialogBody,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Label,
} from '@camunda/design-system';
import {themeStore} from '#/shared/theme/theme';
import {storeStateLocally} from '#/shared/browser-storage/local-storage';
import {HELPER_STORAGE_KEY} from './isMoveHelperHidden';
import modalDiagramImageLight from '#/operate/assets/modal-diagram-image-ds-light.png';
import modalDiagramImageDark from '#/operate/assets/modal-diagram-image-ds-dark.png';

type Props = {
	open: boolean;
	onClose: () => void;
	onSubmit: () => void;
};

const MoveHelperDialog: React.FC<Props> = observer(({open, onClose, onSubmit}) => {
	const {t} = useTranslation();

	return (
		<Dialog
			open={open}
			onOpenChange={(nextOpen) => {
				if (!nextOpen) {
					onClose();
				}
			}}
		>
			<DialogContent size="lg" dismissOnOutsideClick={false}>
				<DialogHeader>
					<DialogTitle>{t('operate.processes.batchModification.helperTitle')}</DialogTitle>
					<DialogDescription>{t('operate.processes.batchModification.helperDescription')}</DialogDescription>
				</DialogHeader>
				<DialogBody className="flex flex-col gap-4 text-sm">
					<p>{t('operate.processes.batchModification.helperSelectTarget')}</p>
					<img
						src={themeStore.actualTheme === 'light' ? modalDiagramImageLight : modalDiagramImageDark}
						alt={t('operate.processes.batchModification.helperImageAlt')}
						className="w-[335px] max-w-full self-center"
					/>
					<p>{t('operate.processes.batchModification.helperReview')}</p>
					<div className="flex items-center gap-2">
						<Checkbox
							id="hide-move-modification-helper"
							onCheckedChange={(checked) => storeStateLocally(HELPER_STORAGE_KEY, checked === true)}
						/>
						<Label htmlFor="hide-move-modification-helper">{t('operate.processes.batchModification.hideHelper')}</Label>
					</div>
				</DialogBody>
				<DialogFooter>
					<Button variant="secondary" onClick={onClose}>
						{t('operate.processes.toolbar.cancel')}
					</Button>
					<Button onClick={onSubmit}>{t('operate.processes.batchModification.continue')}</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
});

export {MoveHelperDialog};
