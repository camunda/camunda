/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {RefObject} from 'react';
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
import type {Variable} from 'v1/api/types';
import {Field, useFormState} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import {DelayedErrorField} from './DelayedErrorField';
import {LoadingTextarea} from './LoadingTextarea';
import {OnNewVariableAdded} from './OnNewVariableAdded';
import {mergeValidators} from './mergeValidators';
import type {FormValues} from '../types';
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
import styles from './styles.module.scss';
import cn from 'classnames';
import {useTranslation} from 'react-i18next';

type Props = {
  containerRef: RefObject<HTMLElement | null>;
  variables: Variable[];
  readOnly: boolean;
  fetchFullVariable: (id: string) => void;
  variablesLoadingFullValue: string[];
  onEdit: (id: string) => void;
};

const VariableEditor: React.FC<Props> = ({
  containerRef,
  variables,
  readOnly,
  fetchFullVariable,
  variablesLoadingFullValue,
  onEdit,
}) => {
  const {dirtyFields} = useFormState<FormValues, Partial<FormValues>>();
  const {t} = useTranslation();

  return (
    <StructuredListWrapper className={styles.list} isCondensed>
      <StructuredListHead>
        <StructuredListRow head>
          <StructuredListCell className={styles.listCell} head>
            {t('variableEditorVariableNameHeader')}
          </StructuredListCell>
          <StructuredListCell className={styles.listCell} head>
            {t('variableEditorVariableValueHeader')}
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
                        labelText={`${variable.name} ${t('tofix_taskVariablesNamedValueLabel')}`}
                        placeholder={`${variable.name} ${t('tofix_taskVariablesNamedValueLabel')}`}
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
                      label={t('variableEditorOpenJsonLabel')}
                      onClick={() => {
                        if (variable.isValueTruncated) {
                          fetchFullVariable(variable.id);
                        }

                        onEdit(createVariableFieldName(variable.name));
                      }}
                      size="sm"
                      kind="ghost"
                      align="top-end"
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
                              labelText={t('variableEditorVariableNameLabel', {
                                count: index + 1,
                                ordinal: true,
                              })}
                              hideLabel
                              placeholder={t('variableEditorNamePlaceholder')}
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
                              labelText={t('variableEditorVariableValueLabel', {
                                count: index + 1,
                                ordinal: true,
                              })}
                              hideLabel
                              invalid={meta.error !== undefined}
                              invalidText={meta.error}
                              placeholder={t('taskVariablesValueLabel')}
                            />
                          )}
                        </DelayedErrorField>
                      </StructuredListCell>
                      <StructuredListCell
                        className={cn(styles.listCell, styles.controlsCell)}
                      >
                        <div className={styles.iconButtons}>
                          <IconButton
                            label={t('variableEditorOpenJsonLabel')}
                            onClick={() => {
                              onEdit(valueFieldName);
                            }}
                            size="sm"
                            kind="ghost"
                            align="top-end"
                            leaveDelayMs={100}
                          >
                            <Popup />
                          </IconButton>
                          <IconButton
                            label={t('taskVariablesRemoveVariable', {
                              count: index + 1,
                              ordinal: true,
                            })}
                            onClick={() => {
                              fields.remove(index);
                            }}
                            size="sm"
                            kind="ghost"
                            align="top-end"
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
