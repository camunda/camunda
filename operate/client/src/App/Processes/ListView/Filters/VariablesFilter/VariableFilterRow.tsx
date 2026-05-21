/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Dropdown, TextInput} from '@carbon/react';
import {Close, Maximize} from '@carbon/react/icons';
import {Field, useForm} from 'react-final-form';
import {IconTextInput} from 'modules/components/IconInput';
import type {VariableFilterOperator} from 'modules/stores/variableFilter';
import {VARIABLE_FILTER_OPERATORS} from './constants';
import {FilterRow, ValueFieldContainer, DeleteButton} from './styled';

type Props = {
  fieldName: string;
  onDelete: () => void;
  isDeleteHidden: boolean;
  rowIndex: number;
  onEditValue: (index: number) => void;
};

const getValuePlaceholder = (operator: VariableFilterOperator): string => {
  switch (operator) {
    case 'contains':
      return 'search text';
    case 'oneOf':
      return '["val1", "val2"]';
    default:
      return 'value in JSON format';
  }
};

const VariableFilterRow: React.FC<Props> = ({
  fieldName,
  onDelete,
  isDeleteHidden,
  rowIndex,
  onEditValue,
}) => {
  const form = useForm();

  return (
    <FilterRow>
      <Field
        name={`${fieldName}.name`}
        subscription={{
          value: true,
          submitError: true,
          dirtySinceLastSubmit: true,
        }}
      >
        {({input, meta}) => (
          <TextInput
            id={input.name}
            name={input.name}
            labelText="Name"
            hideLabel
            placeholder="Variable name"
            value={input.value}
            onChange={input.onChange}
            onBlur={input.onBlur}
            invalid={
              !meta.dirtySinceLastSubmit && meta.submitError !== undefined
            }
            invalidText={
              meta.dirtySinceLastSubmit ? undefined : meta.submitError
            }
            size="sm"
            autoComplete="off"
            data-testid={`variable-filter-name-${rowIndex}`}
          />
        )}
      </Field>
      <Field<VariableFilterOperator>
        name={`${fieldName}.operator`}
        subscription={{value: true}}
      >
        {({input: operatorInput}) => {
          const selectedOperator = VARIABLE_FILTER_OPERATORS.find(
            (op) => op.id === operatorInput.value,
          );
          const isValueRequired = selectedOperator?.requiresValue ?? true;

          return (
            <>
              <Dropdown
                id={operatorInput.name}
                titleText="Operator"
                hideLabel
                label="Select condition"
                items={VARIABLE_FILTER_OPERATORS}
                itemToString={(item) => item?.label ?? ''}
                selectedItem={selectedOperator ?? null}
                onChange={({selectedItem}) => {
                  const newOperator: VariableFilterOperator =
                    selectedItem?.id ?? 'equals';
                  operatorInput.onChange(newOperator);
                  if (!selectedItem?.requiresValue) {
                    form.change(`${fieldName}.value`, '');
                  }
                }}
                size="sm"
                direction={rowIndex < 2 ? 'bottom' : 'top'}
                data-testid={`variable-filter-operator-${rowIndex}`}
              />
              <ValueFieldContainer>
                {isValueRequired && (
                  <Field
                    name={`${fieldName}.value`}
                    subscription={{
                      value: true,
                      submitError: true,
                      dirtySinceLastSubmit: true,
                    }}
                  >
                    {({input, meta}) => (
                      <IconTextInput
                        id={input.name}
                        name={input.name}
                        labelText="Value"
                        hideLabel
                        placeholder={getValuePlaceholder(operatorInput.value)}
                        value={input.value}
                        onChange={input.onChange}
                        onBlur={input.onBlur}
                        invalid={
                          !meta.dirtySinceLastSubmit &&
                          meta.submitError !== undefined
                        }
                        invalidText={
                          meta.dirtySinceLastSubmit
                            ? undefined
                            : meta.submitError
                        }
                        size="sm"
                        Icon={Maximize}
                        buttonLabel="Open JSON editor"
                        onIconClick={() => onEditValue(rowIndex)}
                        data-testid={`variable-filter-value-${rowIndex}`}
                      />
                    )}
                  </Field>
                )}
              </ValueFieldContainer>
            </>
          );
        }}
      </Field>
      <DeleteButton
        kind="ghost"
        size="sm"
        label="Remove condition"
        align="top-right"
        onClick={onDelete}
        data-testid={`delete-variable-filter-${rowIndex}`}
        $hidden={isDeleteHidden}
      >
        <Close />
      </DeleteButton>
    </FilterRow>
  );
};

export {VariableFilterRow};
