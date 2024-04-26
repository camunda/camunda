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

import {RefObject} from 'react';
import {
  IconButton,
  StructuredListBody,
  StructuredListCell,
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
  TextInput,
} from '@carbon/react';
import {Popup, Close} from '@carbon/react/icons';
import {Variable} from 'modules/types';
import {Field, useFormState} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import {FormValues} from '../types';
import {DelayedErrorField} from '../../DelayedErrorField';
import {LoadingTextarea} from '../LoadingTextarea';
import {OnNewVariableAdded} from '../OnNewVariableAdded';
import {
  createVariableFieldName,
  createNewVariableFieldName,
} from '../createVariableFieldName';
import {
  validateValueJSON,
  validateNameCharacters,
  validateNameComplete,
  validateDuplicateNames,
  validateValueComplete,
} from '../validators';
import {mergeValidators} from '../validators/mergeValidators';
import styles from './styles.module.scss';
import cn from 'classnames';

type Props = {
  containerRef: RefObject<HTMLElement | null>;
  variables: Variable[];
  readOnly: boolean;
  fetchFullVariable: (id: string) => void;
  variablesLoadingFullValue: string[];
  onEdit: (id: string) => void;
};

const CODE_EDITOR_BUTTON_TOOLTIP_LABEL = 'Open JSON code editor';

function variableIndexToOrdinal(numberValue: number): string {
  const realOrderIndex = (numberValue + 1).toString();

  if (['11', '12', '13'].includes(realOrderIndex.slice(-2))) {
    return `${realOrderIndex}th`;
  }

  switch (realOrderIndex.slice(-1)) {
    case '1':
      return `${realOrderIndex}st`;
    case '2':
      return `${realOrderIndex}nd`;
    case '3':
      return `${realOrderIndex}rd`;
    default:
      return `${realOrderIndex}th`;
  }
}

const VariableEditor: React.FC<Props> = ({
  containerRef,
  variables,
  readOnly,
  fetchFullVariable,
  variablesLoadingFullValue,
  onEdit,
}) => {
  const {dirtyFields} = useFormState<FormValues, Partial<FormValues>>();

  return (
    <StructuredListWrapper className={styles.list} isCondensed>
      <StructuredListHead>
        <StructuredListRow head>
          <StructuredListCell className={styles.listCell} head>
            Name
          </StructuredListCell>
          <StructuredListCell className={styles.listCell} head>
            Value
          </StructuredListCell>
          <StructuredListCell className={styles.listCell} head />
        </StructuredListRow>
      </StructuredListHead>
      <StructuredListBody>
        {readOnly ? (
          variables.map((variable) => (
            <StructuredListRow key={variable.name}>
              <StructuredListCell
                className={cn(styles.listCell, styles.cellName)}
              >
                {variable.name}
              </StructuredListCell>
              <StructuredListCell
                className={cn(styles.listCell, styles.valueCell)}
              >
                <div className={styles.scrollableOuter}>
                  <div className={styles.scrollableInner}>
                    {variable.isValueTruncated
                      ? `${variable.previewValue}...`
                      : variable.value}
                  </div>
                </div>
              </StructuredListCell>
              <StructuredListCell
                className={cn(styles.listCell, styles.controlsCell)}
              />
            </StructuredListRow>
          ))
        ) : (
          <>
            {variables.map((variable) => (
              <StructuredListRow key={variable.name}>
                <StructuredListCell
                  className={cn(styles.listCell, styles.cellName)}
                >
                  <label htmlFor={createVariableFieldName(variable.name)}>
                    {variable.name}
                  </label>
                </StructuredListCell>
                <StructuredListCell
                  className={cn(styles.listCell, styles.valueCell)}
                >
                  <Field
                    name={createVariableFieldName(variable.name)}
                    validate={
                      variable.isValueTruncated
                        ? () => undefined
                        : validateValueJSON
                    }
                  >
                    {({input, meta}) => (
                      <LoadingTextarea
                        {...input}
                        id={input.name}
                        invalidText={meta.error}
                        isLoading={variablesLoadingFullValue.includes(
                          variable.id,
                        )}
                        onFocus={(event) => {
                          if (variable.isValueTruncated) {
                            fetchFullVariable(variable.id);
                          }
                          input.onFocus(event);
                        }}
                        isActive={meta.active}
                        type="text"
                        labelText={`${variable.name} value`}
                        placeholder={`${variable.name} value`}
                        hideLabel
                      />
                    )}
                  </Field>
                </StructuredListCell>
                <StructuredListCell
                  className={cn(styles.listCell, styles.controlsCell)}
                >
                  <div className={cn(styles.iconButtons, styles.extraPadding)}>
                    <IconButton
                      label={CODE_EDITOR_BUTTON_TOOLTIP_LABEL}
                      onClick={() => {
                        if (variable.isValueTruncated) {
                          fetchFullVariable(variable.id);
                        }

                        onEdit(createVariableFieldName(variable.name));
                      }}
                      size="sm"
                      kind="ghost"
                      leaveDelayMs={100}
                    >
                      <Popup />
                    </IconButton>
                  </div>
                </StructuredListCell>
              </StructuredListRow>
            ))}
            <OnNewVariableAdded
              name="newVariables"
              execute={() => {
                const element = containerRef.current?.parentElement;
                if (element) {
                  element.scrollTop = element.scrollHeight;
                }
              }}
            />
            <FieldArray name="newVariables">
              {({fields}) =>
                fields.map((variable, index) => {
                  const nameFieldName = createNewVariableFieldName(
                    variable,
                    'name',
                  );
                  const valueFieldName = createNewVariableFieldName(
                    variable,
                    'value',
                  );
                  const ordinal = variableIndexToOrdinal(index);

                  return (
                    <StructuredListRow key={variable}>
                      <StructuredListCell
                        className={cn(styles.listCell, styles.cellName)}
                      >
                        <DelayedErrorField
                          name={nameFieldName}
                          validate={mergeValidators(
                            validateNameCharacters,
                            validateNameComplete,
                            validateDuplicateNames,
                          )}
                          addExtraDelay={Boolean(
                            !dirtyFields[nameFieldName] &&
                              dirtyFields[valueFieldName],
                          )}
                        >
                          {({input, meta}) => (
                            <TextInput
                              {...input}
                              id={input.name}
                              invalid={meta.error !== undefined}
                              invalidText={meta.error}
                              type="text"
                              labelText={`${ordinal} variable name`}
                              placeholder="Name"
                              autoFocus
                            />
                          )}
                        </DelayedErrorField>
                      </StructuredListCell>
                      <StructuredListCell
                        className={cn(styles.listCell, styles.valueCell)}
                      >
                        <DelayedErrorField
                          name={valueFieldName}
                          validate={validateValueComplete}
                          addExtraDelay={Boolean(
                            dirtyFields[nameFieldName] &&
                              !dirtyFields[valueFieldName],
                          )}
                        >
                          {({input, meta}) => (
                            <TextInput
                              {...input}
                              id={input.name}
                              type="text"
                              labelText={`${ordinal} variable value`}
                              invalid={meta.error !== undefined}
                              invalidText={meta.error}
                              placeholder="Value"
                            />
                          )}
                        </DelayedErrorField>
                      </StructuredListCell>
                      <StructuredListCell
                        className={cn(styles.listCell, styles.controlsCell)}
                      >
                        <div className={styles.iconButtons}>
                          <IconButton
                            label={CODE_EDITOR_BUTTON_TOOLTIP_LABEL}
                            onClick={() => {
                              onEdit(valueFieldName);
                            }}
                            size="sm"
                            kind="ghost"
                            leaveDelayMs={100}
                          >
                            <Popup />
                          </IconButton>
                          <IconButton
                            label={`Remove ${ordinal} new variable`}
                            onClick={() => {
                              fields.remove(index);
                            }}
                            size="sm"
                            kind="ghost"
                            leaveDelayMs={100}
                          >
                            <Close />
                          </IconButton>
                        </div>
                      </StructuredListCell>
                    </StructuredListRow>
                  );
                })
              }
            </FieldArray>
          </>
        )}
      </StructuredListBody>
    </StructuredListWrapper>
  );
};

export {VariableEditor};
