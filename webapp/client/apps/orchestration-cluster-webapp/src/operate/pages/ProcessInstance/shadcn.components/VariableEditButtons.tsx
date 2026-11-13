/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useForm, useFormState} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import {IconButton, Spinner} from '@camunda/design-system';
import {Check, X} from '@camunda/design-system/icons';
import {useFieldError} from '#/operate/shared/hooks/useFieldError';
import type {VariableFormValues} from '../variableFormValues';

const VariableEditButtons: React.FC<{isSaveBlocked?: boolean}> = ({isSaveBlocked = false}) => {
	const {t} = useTranslation();
	const form = useForm<VariableFormValues>();
	const {values, initialValues, validating, hasValidationErrors, submitting} = useFormState<VariableFormValues>();
	const nameError = useFieldError('name');
	const valueError = useFieldError('value');
	const hasError = (initialValues?.name === '' && nameError !== undefined) || valueError !== undefined;

	return (
		<>
			<IconButton
				type="button"
				variant="ghost"
				size="sm"
				label={t('operate.processInstance.variables.exitDescription')}
				tooltipSide="top"
				icon={X}
				disabled={submitting}
				onClick={() => {
					form.reset({});
				}}
			/>
			{submitting ? (
				<span className="inline-flex size-8 shrink-0 items-center justify-center">
					<Spinner accessibleLabel={t('operate.processInstance.variables.saving')} />
				</span>
			) : (
				<IconButton
					type="button"
					variant="ghost"
					size="sm"
					label={t('operate.processInstance.variables.saveDescription')}
					tooltipSide="top"
					icon={Check}
					disabled={
						isSaveBlocked || initialValues?.value === values.value || validating || hasValidationErrors || hasError
					}
					onClick={() => {
						void form.submit();
					}}
				/>
			)}
		</>
	);
};

export {VariableEditButtons};
