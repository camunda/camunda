/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Trans, useTranslation} from 'react-i18next';
import {
	Button,
	Checkbox,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Label,
} from '@camunda/design-system';
import {setMigrationHelperHidden} from '../migrationHelperPreference';

type Props = {
	open: boolean;
	onClose: () => void;
	onSubmit: () => void;
};

const MigrationHelperModal: React.FC<Props> = ({open, onClose, onSubmit}) => {
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
			<DialogContent size="md" onInteractOutside={(event) => event.preventDefault()}>
				<DialogHeader>
					<DialogTitle>{t('operate.processes.migration.helperTitle')}</DialogTitle>
				</DialogHeader>
				<DialogBody className="flex flex-col gap-4 text-sm">
					<ul className="ml-4 flex list-disc flex-col gap-2">
						<li>{t('operate.processes.migration.helperPurpose')}</li>
						<li>{t('operate.processes.migration.helperImpact')}</li>
						<li>{t('operate.processes.migration.helperPlanning')}</li>
					</ul>
					<p>
						<Trans
							i18nKey="operate.processes.migration.helperDocumentation"
							components={{
								docs: (
									<a
										href="https://docs.camunda.io/docs/components/operate/userguide/process-instance-migration/"
										target="_blank"
										rel="noreferrer"
										className="text-primary-action-default underline"
									/>
								),
							}}
						/>
					</p>
					<div className="flex items-center gap-2">
						<Checkbox
							id="hide-migration-helper"
							onCheckedChange={(checked) => setMigrationHelperHidden(checked === true)}
						/>
						<Label htmlFor="hide-migration-helper">{t('operate.processes.batchModification.hideHelper')}</Label>
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
};

export {MigrationHelperModal};
