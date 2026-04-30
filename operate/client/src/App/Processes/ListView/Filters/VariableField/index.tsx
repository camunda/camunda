/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {tracking} from 'modules/tracking';
import {Title} from 'modules/components/FiltersPanel/styled';
import {Add, Maximize, TrashCan} from '@carbon/react/icons';
import {createPortal} from 'react-dom';
import {Button, Stack, TextInput} from '@carbon/react';
import {IconTextArea} from 'modules/components/IconInput';
import {IconTextInput} from 'modules/components/IconInput';
import {Toggle, VariableValueContainer} from './styled';
import {MultipleValuesModal} from './MultipleValuesModal';
import {variableFilterStore} from 'modules/stores/variableFilter';
import type {Variable} from 'modules/stores/variableFilter';

type EntryState = {
  isMultipleMode: boolean;
  isModalVisible: boolean;
};

type FieldErrors = {
  name?: string;
  values?: string;
};

async function resolveError(
  result: string | undefined | Promise<string | undefined>,
): Promise<string | undefined> {
  if (result === undefined) return undefined;
  if (result instanceof Promise) return result;
  return result;
}

const Variable: React.FC = observer(() => {
  const [entryStates, setEntryStates] = useState<EntryState[]>([
    {isMultipleMode: false, isModalVisible: false},
  ]);
  const [errors, setErrors] = useState<FieldErrors[]>([]);

  useEffect(() => {
    if (variableFilterStore.variables.length === 0) {
      variableFilterStore.setVariables([{name: '', values: ''}]);
    }
    return variableFilterStore.reset;
  }, []);

  const {variables} = variableFilterStore;

  useEffect(() => {
    let cancelled = false;

    const runValidation = async () => {
      const results = await Promise.all(
        variables.map(async (v, i) => {
          const isMultiple =
            (entryStates[i] ?? {isMultipleMode: false}).isMultipleMode;

          const nameError: FieldErrors['name'] = await resolveError(
            validateVariableNameCharacters(v.name) ??
              validateVariableNameComplete(v.name, {variableValues: v.values}),
          );

          const valuesError: FieldErrors['values'] = await resolveError(
            isMultiple
              ? validateMultipleVariableValuesValid(v.values) ??
                  validateVariableValuesComplete(v.values, {
                    variableName: v.name,
                  })
              : validateVariableValueValid(v.values) ??
                  validateVariableValuesComplete(v.values, {
                    variableName: v.name,
                  }),
          );

          return {name: nameError, values: valuesError};
        }),
      );

      if (!cancelled) {
        setErrors(results);
      }
    };

    runValidation();

    return () => {
      cancelled = true;
    };
  }, [variables, entryStates]);

  const getEntryState = (index: number): EntryState =>
    entryStates[index] ?? {isMultipleMode: false, isModalVisible: false};

  const updateVariable = (index: number, update: Partial<Variable>) => {
    const current = variables[index] ?? {name: '', values: ''};
    const updated = [...variables];
    updated[index] = {...current, ...update};
    variableFilterStore.setVariables(updated);
  };

  const addVariable = () => {
    variableFilterStore.setVariables([...variables, {name: '', values: ''}]);
    setEntryStates((prev) => [
      ...prev,
      {isMultipleMode: false, isModalVisible: false},
    ]);
  };

  const removeVariable = (index: number) => {
    variableFilterStore.setVariables(variables.filter((_, i) => i !== index));
    setEntryStates((prev) => prev.filter((_, i) => i !== index));
  };

  const updateEntryState = (index: number, update: Partial<EntryState>) => {
    setEntryStates((prev) => {
      const next = [...prev];
      next[index] = {...getEntryState(index), ...update};
      return next;
    });
  };

  return (
    <>
      <Title>Variable</Title>
      <Stack gap={5}>
        {variables.map((variable, index) => {
          const entryState = getEntryState(index);
          const fieldErrors = errors[index] ?? {};

          return (
            <Stack key={index} gap={3}>
              <TextInput
                value={variable.name}
                id={`variableName-${index}`}
                size="sm"
                data-testid="optional-filter-variable-name"
                labelText="Name"
                autoFocus={index === 0 && variable.name === ''}
                invalid={fieldErrors.name !== undefined}
                invalidText={fieldErrors.name ?? ''}
                onChange={(e) => updateVariable(index, {name: e.target.value})}
              />
              <VariableValueContainer>
                {entryState.isMultipleMode ? (
                  <IconTextArea
                    key={`multipleValues-${index}`}
                    value={variable.values}
                    id={`variableValues-${index}`}
                    placeholder="In JSON format, separated by comma"
                    data-testid="optional-filter-variable-value"
                    labelText="Values"
                    buttonLabel="Open editor modal"
                    onIconClick={() => {
                      updateEntryState(index, {isModalVisible: true});
                      tracking.track({
                        eventName: 'json-editor-opened',
                        variant: 'search-multiple-variables',
                      });
                    }}
                    Icon={Maximize}
                    invalid={fieldErrors.values !== undefined}
                    invalidText={fieldErrors.values ?? ''}
                    onChange={(e) =>
                      updateVariable(index, {values: e.target.value})
                    }
                  />
                ) : (
                  <IconTextInput
                    key={`singleValues-${index}`}
                    value={variable.values}
                    id={`variableValues-${index}`}
                    size="sm"
                    placeholder="in JSON format"
                    data-testid="optional-filter-variable-value"
                    labelText="Value"
                    buttonLabel="Open JSON editor"
                    onIconClick={() => {
                      updateEntryState(index, {isModalVisible: true});
                      tracking.track({
                        eventName: 'json-editor-opened',
                        variant: 'search-variable',
                      });
                    }}
                    Icon={Maximize}
                    invalid={fieldErrors.values !== undefined}
                    invalidText={fieldErrors.values ?? ''}
                    onChange={(e) =>
                      updateVariable(index, {values: e.target.value})
                    }
                  />
                )}
                <Toggle
                  id={`multiple-mode-${index}`}
                  size="sm"
                  labelA="Multiple"
                  labelB="Multiple"
                  aria-label="Multiple"
                  toggled={entryState.isMultipleMode}
                  onToggle={() =>
                    updateEntryState(index, {
                      isMultipleMode: !entryState.isMultipleMode,
                    })
                  }
                />
              </VariableValueContainer>
              {variables.length > 1 && (
                <Button
                  kind="ghost"
                  size="sm"
                  renderIcon={TrashCan}
                  iconDescription="Remove variable"
                  hasIconOnly
                  tooltipPosition="right"
                  onClick={() => removeVariable(index)}
                />
              )}
              {entryState.isModalVisible &&
                (entryState.isMultipleMode
                  ? createPortal(
                      <MultipleValuesModal
                        isVisible={entryState.isModalVisible}
                        initialValue={variable.values}
                        onClose={() => {
                          updateEntryState(index, {isModalVisible: false});
                          tracking.track({
                            eventName: 'json-editor-closed',
                            variant: 'search-multiple-variables',
                          });
                        }}
                        onApply={(value) => {
                          updateVariable(index, {values: value});
                          updateEntryState(index, {isModalVisible: false});
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
                        isVisible={entryState.isModalVisible}
                        title="Edit Variable Value"
                        value={variable.values}
                        onClose={() => {
                          updateEntryState(index, {isModalVisible: false});
                          tracking.track({
                            eventName: 'json-editor-closed',
                            variant: 'search-variable',
                          });
                        }}
                        onApply={(value) => {
                          updateVariable(index, {values: value});
                          updateEntryState(index, {isModalVisible: false});
                          tracking.track({
                            eventName: 'json-editor-saved',
                            variant: 'search-variable',
                          });
                        }}
                      />,
                      document.body,
                    ))}
            </Stack>
          );
        })}
        <Button
          kind="ghost"
          size="sm"
          renderIcon={Add}
          onClick={addVariable}
        >
          Add variable
        </Button>
      </Stack>
    </>
  );
});

export {Variable};
