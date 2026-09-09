/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, Form} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {X} from 'lucide-react';
import {
	Button,
	Dialog,
	DialogBody,
	DialogContent,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	Input,
	Label,
} from '@camunda/design-system';

type Props = {
	isOpen: boolean;
	onApply: (filterName: string) => void;
	onCancel: () => void;
};

const FilterNameModal: React.FC<Props> = ({isOpen, onApply, onCancel}) => {
	const {t} = useTranslation();

	return (
		<Dialog
			open={isOpen}
			onOpenChange={(open) => {
				if (!open) {
					onCancel();
				}
			}}
		>
			<DialogContent
				size="sm"
				showCloseButton={false}
				aria-label={t('tasklist.customFiltersModalSaveAria')}
				aria-describedby={undefined}
				onInteractOutside={(event) => event.preventDefault()}
			>
				{isOpen ? (
					<Form<{filterName: string}>
						onSubmit={(values) => {
							onApply(values.filterName);
						}}
						validate={({filterName}) => {
							const errors: {filterName?: string} = {};

							if (!filterName) {
								errors.filterName = t('tasklist.customFiltersModalNameRequiredError');
							}

							return errors;
						}}
					>
						{({handleSubmit, form}) => (
							<>
								<DialogHeader>
									<DialogTitle>{t('tasklist.customFiltersModalTitle')}</DialogTitle>
									<Button
										type="button"
										variant="ghost"
										size="icon-sm"
										className="absolute top-2 right-2"
										aria-label={t('tasklist.customFiltersModalCancelButton')}
										onClick={onCancel}
									>
										<X aria-hidden />
									</Button>
								</DialogHeader>
								<DialogBody>
									<form onSubmit={handleSubmit}>
										<Field name="filterName" required>
											{({input, meta}) => (
												<div className="flex flex-col gap-1.5">
													<Label htmlFor="filterName">{t('tasklist.customFiltersNameModalFilterNameLabel')}</Label>
													<Input
														id="filterName"
														placeholder={t('tasklist.customFiltersModalNamePlaceholder')}
														required
														value={input.value}
														onChange={input.onChange}
														autoFocus
														aria-invalid={Boolean(meta.error && meta.touched)}
														invalidText={meta.error}
													/>
												</div>
											)}
										</Field>
									</form>
								</DialogBody>
								<DialogFooter>
									<Button type="button" variant="secondary" onClick={onCancel}>
										{t('tasklist.customFiltersModalCancelButton')}
									</Button>
									<Button type="button" onClick={form.submit}>
										{t('tasklist.customFiltersModalSaveAndApplyButton')}
									</Button>
								</DialogFooter>
							</>
						)}
					</Form>
				) : null}
			</DialogContent>
		</Dialog>
	);
};

export {FilterNameModal};
