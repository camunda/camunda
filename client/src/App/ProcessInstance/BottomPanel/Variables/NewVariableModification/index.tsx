/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import {Field, useForm, useFormState} from 'react-final-form';
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
  validateModifiedNameNotDuplicate,
} from '../validators';

type Props = {
  variableName: string;
  onRemove: () => void;
};

const NewVariableModification: React.FC<Props> = ({variableName, onRemove}) => {
  const formState = useFormState();
  const form = useForm();
  const [isModalVisible, setIsModalVisible] = useState(false);
  const valueFieldName = createNewVariableFieldName(variableName, 'value');

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
            {({input, meta}) => (
              <NameField
                {...input}
                type="text"
                placeholder="Name"
                shouldDebounceError={!meta.dirty && formState.dirty}
                autoFocus={true}
              />
            )}
          </Field>
        </FlexContainer>
      </TD>
      <NewValueTD>
        <EditInputContainer>
          <Field
            name={valueFieldName}
            validate={validateModifiedValueComplete}
            parse={(value) => value}
          >
            {({input, meta}) => (
              <ValueField
                {...input}
                type="text"
                placeholder="Value"
                fieldSuffix={{
                  type: 'icon',
                  icon: 'window',
                  press: () => {
                    setIsModalVisible(true);
                  },
                  tooltip: 'Open JSON editor modal',
                }}
                shouldDebounceError={!meta.dirty && formState.dirty}
              />
            )}
          </Field>
          <ActionButtons>
            <ActionButton
              title="Delete Variable"
              onClick={onRemove}
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
        }}
        isVisible={isModalVisible}
      />
    </>
  );
};

export {NewVariableModification};
