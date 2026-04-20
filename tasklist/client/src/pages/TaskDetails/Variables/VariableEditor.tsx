/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type RefObject, useEffect} from 'react';
import {
  IconButton,
  SkeletonText,
  StructuredListBody,
  StructuredListCell,
  StructuredListHead,
  StructuredListRow,
  StructuredListWrapper,
  TextInput,
} from '@carbon/react';
import {useVirtualizer} from '@tanstack/react-virtual';
import {Maximize, Close} from '@carbon/react/icons';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.10';
import {Field, useFormState} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import {DelayedErrorField} from 'modules/tasks/variables-editor/VariableEditor/DelayedErrorField';
import {LoadingTextarea} from 'modules/tasks/variables-editor/VariableEditor/LoadingTextarea';
import {OnNewVariableAdded} from 'modules/tasks/variables-editor/VariableEditor/OnNewVariableAdded';
import {mergeValidators} from 'modules/tasks/variables-editor/VariableEditor/mergeValidators';
import type {FormValues} from 'modules/tasks/variables-editor/types';
import {
  createVariableFieldName,
  createNewVariableFieldName,
} from 'modules/tasks/variables-editor/createVariableFieldName';
import {
  validateValueJSON,
  validateNameCharacters,
  validateNameComplete,
  validateDuplicateNames,
  validateValueComplete,
} from 'modules/tasks/variables-editor/VariableEditor/validators';
import styles from 'modules/tasks/variables-editor/VariableEditor/styles.module.scss';
import cn from 'classnames';
import {useTranslation} from 'react-i18next';

const ESTIMATED_ROW_HEIGHT = 48;
const OVERSCAN = 5;

type Props = {
  containerRef: RefObject<HTMLElement | null>;
  variables: Variable[];
  totalVariables: number;
  readOnly: boolean;
  fetchFullVariable: (variableKey: string) => void;
  variablesLoadingFullValue: string[];
  onEdit: (id: string) => void;
  fetchNextPage: () => void;
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  scrollContainerRef: RefObject<HTMLElement | null>;
};

const VariableEditor: React.FC<Props> = ({
  containerRef,
  variables,
  totalVariables,
  readOnly,
  fetchFullVariable,
  variablesLoadingFullValue,
  onEdit,
  fetchNextPage,
  hasNextPage,
  isFetchingNextPage,
  scrollContainerRef,
}) => {
  const {dirtyFields} = useFormState<FormValues>();
  const {t} = useTranslation();

  const virtualizer = useVirtualizer({
    count: totalVariables,
    getScrollElement: () => scrollContainerRef.current,
    estimateSize: () => ESTIMATED_ROW_HEIGHT,
    overscan: OVERSCAN,
    enabled: readOnly,
  });

  const virtualItems = virtualizer.getVirtualItems();

  useEffect(() => {
    if (!readOnly) {
      return;
    }

    const lastItem = virtualItems.at(-1);
    if (
      lastItem &&
      lastItem.index >= variables.length - 1 &&
      hasNextPage &&
      !isFetchingNextPage
    ) {
      fetchNextPage();
    }
  }, [
    virtualItems,
    variables.length,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
    readOnly,
  ]);

  const paddingTop = virtualItems[0]?.start ?? 0;
  const paddingBottom =
    virtualizer.getTotalSize() - (virtualItems.at(-1)?.end ?? 0);

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
          <>
            {paddingTop > 0 && <div style={{height: paddingTop}} aria-hidden />}
            {virtualItems.map((virtualRow) => {
              const variable = variables[virtualRow.index];

              if (variable === undefined) {
                return (
                  <StructuredListRow key={virtualRow.index}>
                    <StructuredListCell
                      className={cn(styles.listCell, styles.cellName)}
                    >
                      <SkeletonText />
                    </StructuredListCell>
                    <StructuredListCell
                      className={cn(styles.listCell, styles.valueCell)}
                    >
                      <SkeletonText />
                    </StructuredListCell>
                    <StructuredListCell
                      className={cn(styles.listCell, styles.controlsCell)}
                    />
                  </StructuredListRow>
                );
              }

              return (
                <StructuredListRow key={variable.variableKey}>
                  <StructuredListCell
                    className={cn(styles.listCell, styles.cellName)}
                  >
                    {variable.name}
                  </StructuredListCell>
                  <StructuredListCell
                    className={cn(styles.listCell, styles.valueCell)}
                  >
                    <div className={styles.singleLineValue}>
                      {variable.value}
                    </div>
                  </StructuredListCell>
                  <StructuredListCell
                    className={cn(styles.listCell, styles.controlsCell)}
                  >
                    <div
                      className={cn(styles.iconButtons, styles.extraPadding)}
                    >
                      <IconButton
                        label={t('variableEditorOpenJsonLabel')}
                        onClick={() => {
                          if (variable.isTruncated) {
                            fetchFullVariable(variable.variableKey);
                          }
                          onEdit(createVariableFieldName(variable.name));
                        }}
                        size="sm"
                        kind="ghost"
                        align="top-end"
                        leaveDelayMs={100}
                      >
                        <Maximize />
                      </IconButton>
                    </div>
                  </StructuredListCell>
                </StructuredListRow>
              );
            })}
            {paddingBottom > 0 && (
              <div style={{height: paddingBottom}} aria-hidden />
            )}
          </>
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
                      variable.isTruncated ? () => undefined : validateValueJSON
                    }
                  >
                    {({input, meta}) => (
                      <LoadingTextarea
                        {...input}
                        id={input.name}
                        invalidText={meta.error}
                        isLoading={variablesLoadingFullValue.includes(
                          variable.variableKey,
                        )}
                        onFocus={(event) => {
                          if (variable.isTruncated) {
                            fetchFullVariable(variable.variableKey);
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
                        if (variable.isTruncated) {
                          fetchFullVariable(variable.variableKey);
                        }

                        onEdit(createVariableFieldName(variable.name));
                      }}
                      size="sm"
                      kind="ghost"
                      align="top-end"
                      leaveDelayMs={100}
                    >
                      <Maximize />
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
                            !dirtyFields?.[nameFieldName] &&
                            dirtyFields?.[valueFieldName],
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
                            dirtyFields?.[nameFieldName] &&
                            !dirtyFields?.[valueFieldName],
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
                            <Maximize />
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
