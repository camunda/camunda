/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
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
	Input,
	Label,
} from '@camunda/design-system';
import {MigrationDetails} from './MigrationDetails';

type Props = React.ComponentProps<typeof MigrationDetails> & {
	hasElementMapping: boolean;
	onClose: () => void;
	onSubmit: () => void;
};

const MigrationConfirmationModal: React.FC<Props> = ({hasElementMapping, onClose, onSubmit, ...details}) => {
	const {t} = useTranslation();
	const [inputValue, setInputValue] = useState('');
	const isDisabled = inputValue !== 'MIGRATE' || !hasElementMapping;

	return (
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent dismissOnOutsideClick={false}>
				<form
					onSubmit={(event) => {
						event.preventDefault();
						if (!isDisabled) {
							onSubmit();
						}
					}}
				>
					<DialogHeader>
						<DialogTitle>{t('operate.processes.migration.confirmationTitle')}</DialogTitle>
					</DialogHeader>
					<DialogBody className="flex flex-col gap-4 text-sm">
						<DialogDescription asChild>
							<MigrationDetails {...details} />
						</DialogDescription>
						<div className="flex flex-col gap-1.5">
							<Label htmlFor="migration-confirmation">{t('operate.processes.migration.confirmationLabel')}</Label>
							<Input
								autoFocus
								id="migration-confirmation"
								value={inputValue}
								onChange={(event) => setInputValue(event.target.value)}
								aria-invalid={!['MIGRATE', ''].includes(inputValue)}
								invalidText={t('operate.processes.migration.confirmationInvalid')}
							/>
						</div>
					</DialogBody>
					<DialogFooter>
						<Button type="button" variant="secondary" onClick={onClose}>
							{t('operate.processes.toolbar.cancel')}
						</Button>
						<Button type="submit" disabled={isDisabled}>
							{t('operate.processes.migration.confirm')}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	);
};

export {MigrationConfirmationModal};
