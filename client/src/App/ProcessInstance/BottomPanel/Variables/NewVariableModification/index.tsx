/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Field, useField, useForm, useFormState} from 'react-final-form';
import {
  NameField,
  ValueField,
  DeleteIcon,
  FlexContainer,
  ActionButtons,
  NewValueTD,
} from './styled';
import {TD, EditInputContainer} from '../styled';
import {get} from 'lodash';
import {createNewVariableFieldName} from '../createVariableFieldName';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {ActionButton} from 'modules/components/ActionButton';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {
  validateNameCharacters,
  validateModifiedNameComplete,
  validateModifiedValueComplete,
  validateModifiedValueValid,
  validateModifiedNameNotDuplicate,
} from '../validators';
import {modificationsStore} from 'modules/stores/modifications';
import {variablesStore} from 'modules/stores/variables';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';

type Props = {
  variableName: string;
  onRemove: () => void;
};

const createModification = ({
  scopeId,
  areFormFieldsValid,
  id,
  name,
  value,
}: {
  scopeId: string | null;
  areFormFieldsValid: boolean;
  id: string;
  name: string;
  value: string;
}) => {
  if (scopeId === null || !areFormFieldsValid || name === '' || value === '') {
    return;
  }

  const lastAddModification = modificationsStore.getLastVariableModification(
    scopeId,
    id,
    'ADD_VARIABLE'
  );

  if (
    lastAddModification === undefined ||
    lastAddModification.name !== name ||
    lastAddModification.newValue !== value
  ) {
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId,
        id,
        flowNodeName: flowNodeSelectionStore.selectedFlowNodeName,
        name,
        newValue: value,
      },
    });
  }
};

const NewVariableModification: React.FC<Props> = ({variableName, onRemove}) => {
  const formState = useFormState();
  const form = useForm();
  const [isModalVisible, setIsModalVisible] = useState(false);
  const valueFieldName = createNewVariableFieldName(variableName, 'value');

  const {
    meta: {valid: isNameValid, validating: isNameValidating},
    input: {value: currentName},
  } = useField(createNewVariableFieldName(variableName, 'name'));

  const {
    meta: {valid: isValueValid, validating: isValueValidating},
    input: {value: currentValue},
  } = useField(createNewVariableFieldName(variableName, 'value'));

  const {
    input: {value: currentId},
  } = useField(createNewVariableFieldName(variableName, 'id'));

  const isNameFieldValid = (!isNameValidating && isNameValid) ?? false;
  const isValueFieldValid = (!isValueValidating && isValueValid) ?? false;
  const areFormFieldsValid = isNameFieldValid && isValueFieldValid;
  const scopeId =
    variablesStore.scopeId ??
    modificationsStore.getNewScopeIdForFlowNode(
      flowNodeSelectionStore.state.selection?.flowNodeId
    );

  return (
    <>
      <TD>
        <FlexContainer>
          <Field
            name={createNewVariableFieldName(variableName, 'name')}
            validate={mergeValidators(
              validateNameCharacters,
              validateModifiedNameComplete,
              validateModifiedNameNotDuplicate
            )}
            allowNull={false}
            parse={(value) => value}
          >
            {({input}) => (
              <NameField
                {...input}
                type="text"
                data-testid="new-variable-name"
                placeholder="Name"
                shouldDebounceError={false}
                autoFocus={input.value === ''}
                onBlur={() => {
                  form.mutators?.triggerValidation?.(
                    createNewVariableFieldName(variableName, 'name')
                  );

                  input.onBlur();

                  createModification({
                    scopeId,
                    areFormFieldsValid,
                    id: currentId,
                    name: currentName,
                    value: currentValue,
                  });
                }}
              />
            )}
          </Field>
        </FlexContainer>
      </TD>
      <NewValueTD>
        <EditInputContainer>
          <Field
            name={valueFieldName}
            validate={mergeValidators(
              validateModifiedValueComplete,
              validateModifiedValueValid
            )}
            parse={(value) => value}
          >
            {({input}) => (
              <ValueField
                {...input}
                type="text"
                data-testid="new-variable-value"
                placeholder="Value"
                shouldDebounceError={false}
                fieldSuffix={{
                  type: 'icon',
                  icon: 'window',
                  press: () => {
                    setIsModalVisible(true);
                  },
                  tooltip: 'Open JSON editor modal',
                }}
                onBlur={() => {
                  form.mutators?.triggerValidation?.(valueFieldName);
                  input.onBlur();

                  createModification({
                    scopeId,
                    areFormFieldsValid,
                    id: currentId,
                    name: currentName,
                    value: currentValue,
                  });
                }}
              />
            )}
          </Field>
          <ActionButtons>
            <ActionButton
              title="Delete Variable"
              onClick={() => {
                onRemove();
                modificationsStore.removeVariableModification(
                  scopeId!,
                  currentId,
                  'ADD_VARIABLE',
                  'variables'
                );
              }}
              icon={<DeleteIcon />}
            />
          </ActionButtons>
        </EditInputContainer>
      </NewValueTD>

      <JSONEditorModal
        title="Edit a new Variable"
        value={get(formState.values, valueFieldName)}
        onClose={() => {
          setIsModalVisible(false);
        }}
        onApply={(value) => {
          form.change(valueFieldName, value);
          setIsModalVisible(false);
          if (value !== undefined) {
            createModification({
              scopeId,
              areFormFieldsValid:
                (isNameFieldValid &&
                  form.getFieldState(valueFieldName)?.valid) ??
                false,
              id: currentId,
              name: currentName,
              value: value,
            });
          }
        }}
        isVisible={isModalVisible}
      />
    </>
  );
};

export {NewVariableModification};
