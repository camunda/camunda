/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  validateValueComplete,
  validateValueNotEmpty,
  validateValueValid,
} from './validators';
import {Field, useFormState} from 'react-final-form';
import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {LoadingTextfield} from './LoadingTextField';
import {Layer} from '@carbon/react';
import {useVariable} from 'modules/queries/variables/useVariable';
import {notificationsStore} from 'modules/stores/notifications';
import {useExistingVariableEditor} from 'modules/hooks/useExistingVariableEditor';

type Props = {
  id?: string;
  variableName: string;
  variableValue: string;
  isPreview?: boolean;
};

const ExistingVariableValue: React.FC<Props> = observer(
  ({id, variableName, variableValue, isPreview}) => {
    const {isModificationModeEnabled} = modificationsStore;
    const formState = useFormState();

    const variableEditor = useExistingVariableEditor(
      variableName,
      variableValue,
    );

    const {
      data: variable,
      isLoading,
      error,
    } = useVariable(id!, {
      enabled: isPreview && id !== undefined,
    });

    useEffect(() => {
      if (error) {
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Variable could not be fetched',
          isDismissable: true,
        });
      }
    }, [error]);

    const isVariableValueUndefined = variable?.value === undefined;
    const pauseValidation = isPreview && isVariableValueUndefined;

    return (
      <Layer>
        <Field
          name={variableEditor.fieldName}
          initialValue={variableEditor.getInitialValue(variable)}
          validate={
            pauseValidation
              ? () => undefined
              : mergeValidators(
                  validateValueComplete,
                  validateValueValid,
                  validateValueNotEmpty,
                )
          }
          parse={(value) => value}
        >
          {({input, meta}) => (
            <LoadingTextfield
              {...input}
              size="sm"
              type="text"
              id={variableEditor.fieldName}
              hideLabel
              disabled={formState.submitting}
              labelText="Value"
              placeholder="Value"
              data-testid="edit-variable-value"
              autoFocus={!isModificationModeEnabled || meta.active}
              isLoading={isLoading}
              onFocus={(event) => {
                if (!meta.active) {
                  input.onFocus(event);
                }
              }}
              onBlur={(event) => {
                variableEditor.createModification({
                  scopeId: variableEditor.variableScopeKey,
                  name: variableName,
                  oldValue: variableEditor.getInitialValue(variable),
                  newValue: input.value ?? '',
                  isDirty:
                    variableEditor.getInitialValue(variable) !== input.value,
                  isValid: variableEditor.isValid ?? false,
                  selectedElementName: variableEditor.selectedElementName,
                });

                input.onBlur(event);
              }}
            />
          )}
        </Field>
      </Layer>
    );
  },
);

export {ExistingVariableValue};
