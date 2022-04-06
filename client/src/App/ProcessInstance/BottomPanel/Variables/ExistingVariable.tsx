/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  ValueField,
  TD,
  VariableName,
  EditInputTD,
  EditButtonsTD,
} from './styled';
import {validateValueComplete} from './validators';
import {Field, useForm, useFormState} from 'react-final-form';
import {useRef, useState} from 'react';
import {EditButtons} from './EditButtons';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';

type Props = {
  variableName: string;
  variableValue: string;
};

const ExistingVariable: React.FC<Props> = ({variableName, variableValue}) => {
  const formState = useFormState();
  const [isModalVisible, setIsModalVisible] = useState(false);
  const form = useForm();

  const editInputTDRef = useRef<HTMLTableDataCellElement>(null);

  return (
    <>
      <TD>
        <Field name="name" initialValue={variableName}>
          {() => {
            return (
              <VariableName title={variableName}>{variableName}</VariableName>
            );
          }}
        </Field>
      </TD>

      <EditInputTD ref={editInputTDRef}>
        <Field
          name="value"
          initialValue={variableValue}
          validate={validateValueComplete}
          parse={(value) => value}
        >
          {({input}) => (
            <ValueField
              {...input}
              type="text"
              placeholder="Value"
              data-testid="edit-variable-value"
              fieldSuffix={{
                type: 'icon',
                icon: 'window',
                press: () => {
                  setIsModalVisible(true);
                },
                tooltip: 'Open JSON editor modal',
              }}
              shouldDebounceError={false}
              autoFocus={true}
            />
          )}
        </Field>
      </EditInputTD>
      <EditButtonsTD>
        <EditButtons />
      </EditButtonsTD>
      <JSONEditorModal
        title={`Edit Variable "${variableName}"`}
        value={formState.values?.value}
        onClose={() => {
          setIsModalVisible(false);
        }}
        onSave={(value) => {
          form.change('value', value);
          setIsModalVisible(false);
        }}
        isModalVisible={isModalVisible}
      />
    </>
  );
};

export {ExistingVariable};
