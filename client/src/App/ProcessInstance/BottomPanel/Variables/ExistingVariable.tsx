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
  EditInputContainer,
} from './styled';
import {validateValueComplete} from './validators';
import {Field, useForm, useFormState} from 'react-final-form';
import {useRef, useState} from 'react';
import {EditButtons} from './EditButtons';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {tracking} from 'modules/tracking';
import {observer} from 'mobx-react';
import {modificationsStore} from 'modules/stores/modifications';
import {createVariableFieldName} from './createVariableFieldName';

type Props = {
  variableName: string;
  variableValue: string;
};

const ExistingVariable: React.FC<Props> = observer(
  ({variableName, variableValue}) => {
    const {isModificationModeEnabled} = modificationsStore;
    const formState = useFormState();
    const [isModalVisible, setIsModalVisible] = useState(false);
    const form = useForm();

    const editInputTDRef = useRef<HTMLTableCellElement | null>(null);

    const fieldName = isModificationModeEnabled
      ? createVariableFieldName(variableName)
      : 'value';
    return (
      <>
        <TD>
          <VariableName title={variableName}>{variableName}</VariableName>
        </TD>

        <EditInputTD ref={editInputTDRef}>
          <EditInputContainer>
            <Field
              name={fieldName}
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
                      tracking.track({
                        eventName: 'json-editor-opened',
                        variant: 'edit-variable',
                      });
                    },
                    tooltip: 'Open JSON editor modal',
                  }}
                  shouldDebounceError={false}
                  autoFocus={!isModificationModeEnabled}
                />
              )}
            </Field>
            {!isModificationModeEnabled && <EditButtons />}
          </EditInputContainer>
        </EditInputTD>
        <JSONEditorModal
          isVisible={isModalVisible}
          title={`Edit Variable "${variableName}"`}
          value={formState.values?.[fieldName]}
          onClose={() => {
            setIsModalVisible(false);
            tracking.track({
              eventName: 'json-editor-closed',
              variant: 'edit-variable',
            });
          }}
          onApply={(value) => {
            form.change(fieldName, value);
            setIsModalVisible(false);
            tracking.track({
              eventName: 'json-editor-saved',
              variant: 'edit-variable',
            });
          }}
        />
      </>
    );
  }
);

export {ExistingVariable};
