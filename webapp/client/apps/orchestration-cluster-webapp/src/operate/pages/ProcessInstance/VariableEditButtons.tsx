/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useForm, useFormState} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {Checkmark, Close} from '@carbon/react/icons';
import {useFieldError} from '#/operate/shared/hooks/useFieldError';
import type {VariableFormValues} from './variableFormValues';
import {SavingLoading, SavingLoadingContainer} from './variables.styled';

const VariableEditButtons: React.FC<{isSaveBlocked?: boolean}> = ({isSaveBlocked = false}) => {
	const {t} = useTranslation();
	const form = useForm<VariableFormValues>();
	const {values, initialValues, validating, hasValidationErrors, submitting} = useFormState<VariableFormValues>();
	const nameError = useFieldError('name');
	const valueError = useFieldError('value');
	const hasError = (initialValues?.name === '' && nameError !== undefined) || valueError !== undefined;

	return (
		<>
			<Button
				kind="ghost"
				size="sm"
				iconDescription={t('operate.processInstance.variables.exitDescription')}
				aria-label={t('operate.processInstance.variables.exit')}
				tooltipPosition="top"
				onClick={() => {
					form.reset({});
				}}
				hasIconOnly
				renderIcon={Close}
				disabled={submitting}
			/>
			{submitting ? (
				<SavingLoadingContainer>
					<SavingLoading small withOverlay={false} data-testid="full-variable-loader" />
				</SavingLoadingContainer>
			) : (
				<Button
					kind="ghost"
					size="sm"
					iconDescription={t('operate.processInstance.variables.saveDescription')}
					aria-label={t('operate.processInstance.variables.save')}
					tooltipPosition="top"
					disabled={
						isSaveBlocked || initialValues?.value === values.value || validating || hasValidationErrors || hasError
					}
					onClick={() => {
						void form.submit();
					}}
					hasIconOnly
					renderIcon={Checkmark}
				/>
			)}
		</>
	);
};

export {VariableEditButtons};
