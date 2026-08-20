/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComposedModal, ModalHeader, ModalBody, ModalFooter, TextInput, Button} from '@carbon/react';
import {Field, Form} from 'react-final-form';
import {useTranslation} from 'react-i18next';

type Props = {
	isOpen: boolean;
	onApply: (filterName: string) => void;
	onCancel: () => void;
};

const FilterNameModal: React.FC<Props> = ({isOpen, onApply, onCancel}) => {
	const {t} = useTranslation();

	return (
		<ComposedModal
			open={isOpen}
			aria-label={t('tasklist.customFiltersModalSaveAria')}
			preventCloseOnClickOutside
			size="sm"
			onClose={onCancel}
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
							<ModalHeader title={t('tasklist.customFiltersModalTitle')} buttonOnClick={onCancel} />
							<ModalBody hasForm>
								<form onSubmit={handleSubmit}>
									<Field name="filterName" required>
										{({input, meta}) => (
											<TextInput
												id="filterName"
												labelText={t('tasklist.customFiltersNameModalFilterNameLabel')}
												placeholder={t('tasklist.customFiltersModalNamePlaceholder')}
												required
												value={input.value}
												onChange={input.onChange}
												data-modal-primary-focus
												invalid={Boolean(meta.error && meta.touched)}
												invalidText={meta.error}
											/>
										)}
									</Field>
								</form>
							</ModalBody>
							<ModalFooter>
								<Button kind="secondary" onClick={onCancel}>
									{t('tasklist.customFiltersModalCancelButton')}
								</Button>
								<Button kind="primary" onClick={form.submit}>
									{t('tasklist.customFiltersModalSaveAndApplyButton')}
								</Button>
							</ModalFooter>
						</>
					)}
				</Form>
			) : null}
		</ComposedModal>
	);
};

export {FilterNameModal};
