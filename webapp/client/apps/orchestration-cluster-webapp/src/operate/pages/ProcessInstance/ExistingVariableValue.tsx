/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {Field} from 'react-final-form';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Layer, Loading} from '@carbon/react';
import {InlineJsonEditor} from '#/operate/shared/Editors/InlineJsonEditor';
import {useFieldError} from '#/operate/shared/hooks/useFieldError';
import {mergeValidators} from '#/operate/shared/utils/mergeValidators';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useExistingVariableEditor} from './useExistingVariableEditor';
import {variableQuery} from './variables.queries';
import {validateValueComplete, validateValueNotEmpty, validateValueValid} from './variableValidators';

type Props = {
	variableKey: string;
	variableName: string;
	variableValue: string;
	isTruncated: boolean;
};

const validateValue = mergeValidators(validateValueComplete, validateValueValid, validateValueNotEmpty);

const ExistingVariableValue: React.FC<Props> = ({variableKey, variableName, variableValue, isTruncated}) => {
	const {t} = useTranslation();
	const {isModificationModeEnabled} = useProcessInstanceModification();
	const variableEditor = useExistingVariableEditor(variableName, variableValue);
	const {data: variable, isLoading, error} = useQuery({...variableQuery(variableKey), enabled: isTruncated});
	const fieldError = useFieldError(variableEditor.fieldName);
	const isFullValueMissing = isTruncated && variable === undefined;

	useEffect(() => {
		if (error !== null) {
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('operate.processInstance.variables.fetchFailed'),
				isDismissable: true,
			});
		}
	}, [error, t]);

	return (
		<Layer>
			<Field
				name={variableEditor.fieldName}
				initialValue={variableEditor.getInitialValue(isTruncated ? variable?.value : undefined)}
				validate={isFullValueMissing ? () => undefined : validateValue}
				parse={(value) => value}
			>
				{({input, meta}) => (
					<>
						{isLoading && <Loading small data-testid="full-variable-loader" />}
						<InlineJsonEditor
							value={input.value}
							onChange={input.onChange}
							onFocus={input.onFocus}
							id={variableEditor.fieldName}
							label={variableName}
							fieldError={fieldError}
							autoFocus={!isModificationModeEnabled || meta.active}
							readOnly={meta.submitting || isFullValueMissing}
							isModified={meta.modified}
							onBlur={() => {
								if (isFullValueMissing) {
									input.onBlur();
									return;
								}
								const oldValue = variableEditor.getInitialValue(isTruncated ? variable?.value : undefined);
								variableEditor.stageModification({oldValue, newValue: input.value ?? ''});
								input.onBlur();
							}}
						/>
					</>
				)}
			</Field>
		</Layer>
	);
};

export {ExistingVariableValue};
