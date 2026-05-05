/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Dropdown, TextInput} from '@carbon/react';
import {Close, Maximize} from '@carbon/react/icons';
import {createPortal} from 'react-dom';
import {Field, useField, useForm} from 'react-final-form';
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {IconTextInput} from 'modules/components/IconInput';
import {useFieldError} from 'modules/hooks/useFieldError';
import type {VariableFilterOperator} from 'modules/stores/variableFilter';
import {VARIABLE_FILTER_OPERATORS} from './constants';
import {FilterRow, ValueFieldContainer, DeleteButton} from './styled';

type Props = {
  fieldName: string;
  onDelete: () => void;
  isDeleteHidden: boolean;
  rowIndex: number;
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
}) => {
  const [isJsonEditorOpen, setIsJsonEditorOpen] = useState(false);
  const form = useForm();

  const {
    input: {value: fieldId},
  } = useField<string>(`${fieldName}.id`, {subscription: {value: true}});
  const {
    input: {value: operatorValue},
  } = useField<VariableFilterOperator>(`${fieldName}.operator`, {
    subscription: {value: true},
  });

  const nameError = useFieldError(`${fieldName}.name`);
  const valueError = useFieldError(`${fieldName}.value`);

  const selectedOperator = VARIABLE_FILTER_OPERATORS.find(
    (op) => op.id === operatorValue,
  );
  const isValueRequired = selectedOperator?.requiresValue ?? true;

  const handleOperatorChange = (
    selectedItem: (typeof VARIABLE_FILTER_OPERATORS)[number] | null,
  ) => {
    const newOperator: VariableFilterOperator = selectedItem?.id ?? 'equals';
    form.change(`${fieldName}.operator`, newOperator);
    if (!selectedItem?.requiresValue) {
      form.change(`${fieldName}.value`, '');
    }
  };

  return (
    <>
      <FilterRow>
        <Field name={`${fieldName}.name`} subscription={{value: true}}>
          {({input}) => (
            <TextInput
              id={`variable-name-${fieldId}`}
              labelText="Name"
              hideLabel
              placeholder="Variable name"
              value={input.value}
              onChange={input.onChange}
              onBlur={input.onBlur}
              invalid={nameError !== undefined}
              invalidText={nameError}
              size="sm"
              autoComplete="off"
              data-testid={`variable-filter-name-${fieldId}`}
            />
          )}
        </Field>
        <Dropdown
          id={`variable-operator-${fieldId}`}
          titleText="Operator"
          hideLabel
          label="Select condition"
          items={VARIABLE_FILTER_OPERATORS}
          itemToString={(item) => item?.label ?? ''}
          selectedItem={selectedOperator ?? null}
          onChange={({selectedItem}) => handleOperatorChange(selectedItem)}
          size="sm"
          direction={rowIndex < 2 ? 'bottom' : 'top'}
          data-testid={`variable-filter-operator-${fieldId}`}
        />
        <ValueFieldContainer>
          {isValueRequired && (
            <Field name={`${fieldName}.value`} subscription={{value: true}}>
              {({input}) => (
                <>
                  <IconTextInput
                    id={`variable-value-${fieldId}`}
                    labelText="Value"
                    hideLabel
                    placeholder={getValuePlaceholder(operatorValue)}
                    value={input.value}
                    onChange={input.onChange}
                    onBlur={input.onBlur}
                    invalid={valueError !== undefined}
                    invalidText={valueError}
                    size="sm"
                    Icon={Maximize}
                    buttonLabel="Open JSON editor"
                    onIconClick={() => setIsJsonEditorOpen(true)}
                    data-testid={`variable-filter-value-${fieldId}`}
                  />
                  {isJsonEditorOpen &&
                    createPortal(
                      <JSONEditorModal
                        isVisible={isJsonEditorOpen}
                        title="Edit Variable Value"
                        value={input.value}
                        onClose={() => setIsJsonEditorOpen(false)}
                        onApply={(value) => {
                          input.onChange(value ?? '');
                          setIsJsonEditorOpen(false);
                        }}
                      />,
                      document.body,
                    )}
                </>
              )}
            </Field>
          )}
        </ValueFieldContainer>
        <DeleteButton
          kind="ghost"
          size="sm"
          label="Remove condition"
          align="top-right"
          onClick={onDelete}
          data-testid={`delete-variable-filter-${fieldId}`}
          $hidden={isDeleteHidden}
        >
          <Close />
        </DeleteButton>
      </FilterRow>
    </>
  );
};

export {VariableFilterRow};
