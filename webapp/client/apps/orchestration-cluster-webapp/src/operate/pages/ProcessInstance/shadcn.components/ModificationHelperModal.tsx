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
import {ArrowRight, Ban, Plus} from '@camunda/design-system/icons';
import {themeStore} from '#/shared/theme/theme';
import diagramLight from '#/operate/assets/modification-helper-diagram-light.svg';
import diagramDark from '#/operate/assets/modification-helper-diagram-dark.svg';
import {setModificationHelperHidden} from '../modificationHelperPreference';

type Props = {
	open: boolean;
	onClose: () => void;
	onSubmit: () => void;
};

const ModificationHelperModal: React.FC<Props> = observer(({open, onClose, onSubmit}) => {
	const {t} = useTranslation();
	const rows = [
		{
			Icon: Plus,
			type: t('operate.processInstance.modification.add'),
			description: t('operate.processInstance.modification.helperAdd'),
		},
		{
			Icon: Ban,
			type: t('operate.processInstance.modification.cancel'),
			description: t('operate.processInstance.modification.helperCancel'),
		},
		{
			Icon: ArrowRight,
			type: t('operate.processInstance.modification.move'),
			description: t('operate.processInstance.modification.helperMove'),
		},
	];

	return (
		<Dialog
			open={open}
			onOpenChange={(nextOpen) => {
				if (!nextOpen) {
					onClose();
				}
			}}
		>
			<DialogContent size="md" onInteractOutside={(event) => event.preventDefault()}>
				<DialogHeader>
					<DialogTitle>{t('operate.processInstance.modification.frameTitle')}</DialogTitle>
					<DialogDescription>{t('operate.processInstance.modification.helperIntro')}</DialogDescription>
				</DialogHeader>
				<DialogBody className="flex flex-col gap-4 text-sm">
					<p>{t('operate.processInstance.modification.helperSelect')}</p>
					<ul className="ml-8 flex list-none flex-col gap-2 p-0">
						{rows.map(({Icon, type, description}) => (
							<li key={type} className="flex items-center gap-2">
								<span className="flex min-w-24 items-center justify-between gap-2 font-medium text-primary-action-default">
									{type}
									<Icon aria-hidden="true" className="size-4" />
								</span>
								{description}
							</li>
						))}
					</ul>
					<p>{t('operate.processInstance.modification.helperVariables')}</p>
					<p>{t('operate.processInstance.modification.helperReview')}</p>
					<img
						src={themeStore.actualTheme === 'light' ? diagramLight : diagramDark}
						alt=""
						className="max-w-full self-center"
					/>
					<div className="flex items-center gap-2">
						<Checkbox
							id="hide-modification-helper"
							onCheckedChange={(checked) => setModificationHelperHidden(checked === true)}
						/>
						<Label htmlFor="hide-modification-helper">{t('operate.processes.batchModification.hideHelper')}</Label>
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

export {ModificationHelperModal};
