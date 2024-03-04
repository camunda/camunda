/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {useEffect, useState} from 'react';
import {observer} from 'mobx-react';
import {
  validateVariableNameCharacters,
  validateVariableNameComplete,
  validateVariableValuesComplete,
  validateMultipleVariableValuesValid,
  validateVariableValueValid,
} from 'modules/validators';
import {Field, useForm, useFormState} from 'react-final-form';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {mergeValidators} from 'modules/utils/validators/mergeValidators';
import {tracking} from 'modules/tracking';
import {Title} from 'modules/components/FiltersPanel/styled';
import {Popup} from '@carbon/react/icons';
import {TextInputField} from 'modules/components/TextInputField';
import {createPortal} from 'react-dom';
import {Stack} from '@carbon/react';
import {IconTextAreaField} from 'modules/components/IconTextAreaField';
import {IconTextInputField} from 'modules/components/IconTextInputField';
import {Toggle, VariableValueContainer} from './styled';
import {MultipleValuesModal} from './MultipleValuesModal';
import {variableFilterStore} from 'modules/stores/variableFilter';

const Variable: React.FC = observer(() => {
  const [isModalVisible, setIsModalVisible] = useState(false);
  const formState = useFormState();
  const form = useForm();

  useEffect(() => {
    return variableFilterStore.reset;
  }, []);

  const {
    state: {isInMultipleMode},
    setIsInMultipleMode,
  } = variableFilterStore;

  return (
    <>
      <Title>Variable</Title>
      <Stack gap={5}>
        <Field
          name="variableName"
          validate={mergeValidators(
            validateVariableNameCharacters,
            validateVariableNameComplete,
          )}
        >
          {({input}) => (
            <TextInputField
              {...input}
              id="variableName"
              size="sm"
              data-testid="optional-filter-variable-name"
              labelText="Name"
              autoFocus
            />
          )}
        </Field>
        <VariableValueContainer>
          <Field
            name="variableValues"
            // this key is needed to trigger validation after the user switched multiple mode
            key={isInMultipleMode ? 'multipleValues' : 'singleValues'}
            validate={mergeValidators(
              validateVariableValuesComplete,
              isInMultipleMode
                ? validateMultipleVariableValuesValid
                : validateVariableValueValid,
            )}
          >
            {({input}) => {
              if (isInMultipleMode) {
                return (
                  <IconTextAreaField
                    {...input}
                    id="variableValues"
                    placeholder="In JSON format, separated by comma"
                    data-testid="optional-filter-variable-value"
                    labelText="Values"
                    buttonLabel="Open editor modal"
                    onIconClick={() => {
                      setIsModalVisible(true);
                      tracking.track({
                        eventName: 'json-editor-opened',
                        variant: 'search-multiple-variables',
                      });
                    }}
                    Icon={Popup}
                  />
                );
              } else {
                return (
                  <IconTextInputField
                    {...input}
                    id="variableValues"
                    size="sm"
                    placeholder="in JSON format"
                    data-testid="optional-filter-variable-value"
                    labelText="Value"
                    buttonLabel="Open JSON editor modal"
                    onIconClick={() => {
                      setIsModalVisible(true);
                      tracking.track({
                        eventName: 'json-editor-opened',
                        variant: 'search-variable',
                      });
                    }}
                    Icon={Popup}
                  />
                );
              }
            }}
          </Field>
          <Toggle
            id="multiple-mode"
            size="sm"
            labelA="Multiple"
            labelB="Multiple"
            aria-label="Multiple"
            toggled={isInMultipleMode}
            onToggle={() => {
              setIsInMultipleMode(!isInMultipleMode);
            }}
          />
        </VariableValueContainer>
      </Stack>

      {isInMultipleMode
        ? createPortal(
            <MultipleValuesModal
              isVisible={isModalVisible}
              initialValue={formState.values?.variableValues}
              onClose={() => {
                setIsModalVisible(false);
                tracking.track({
                  eventName: 'json-editor-closed',
                  variant: 'search-multiple-variables',
                });
              }}
              onApply={(value) => {
                form.change('variableValues', value);
                setIsModalVisible(false);
                tracking.track({
                  eventName: 'json-editor-saved',
                  variant: 'search-multiple-variables',
                });
              }}
            />,
            document.body,
          )
        : createPortal(
            <JSONEditorModal
              isVisible={isModalVisible}
              title="Edit Variable Value"
              value={formState.values?.variableValues}
              onClose={() => {
                setIsModalVisible(false);
                tracking.track({
                  eventName: 'json-editor-closed',
                  variant: 'search-variable',
                });
              }}
              onApply={(value) => {
                form.change('variableValues', value);
                setIsModalVisible(false);
                tracking.track({
                  eventName: 'json-editor-saved',
                  variant: 'search-variable',
                });
              }}
            />,
            document.body,
          )}
    </>
  );
});

export {Variable};
