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
import {JSONEditorModal} from 'modules/components/JSONEditorModal';
import {IconTextInput} from 'modules/components/IconInput';
import type {VariableFilterOperator} from 'modules/stores/variableFilter';
import {
  type DraftCondition,
  type RowErrors,
  VARIABLE_FILTER_OPERATORS,
} from './constants';
import {
  FilterRow,
  ConditionDropdownContainer,
  ValueFieldContainer,
  DeleteButton,
} from './styled';

type Props = {
  condition: DraftCondition;
  onChange: (condition: DraftCondition) => void;
  onDelete: () => void;
  isDeleteHidden: boolean;
  rowIndex: number;
  errors: RowErrors;
  onBlur: () => void;
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
  condition,
  onChange,
  onDelete,
  isDeleteHidden,
  rowIndex,
  errors,
  onBlur,
}) => {
  const [isJsonEditorOpen, setIsJsonEditorOpen] = useState(false);

  const selectedOperator = VARIABLE_FILTER_OPERATORS.find(
    (op) => op.id === condition.operator,
  );
  const isValueRequired = selectedOperator?.requiresValue ?? true;

  const handleNameChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    onChange({...condition, name: event.target.value});
  };

  const handleOperatorChange = (
    selectedItem: (typeof VARIABLE_FILTER_OPERATORS)[number] | null,
  ) => {
    const newOperator: VariableFilterOperator = selectedItem?.id ?? 'equals';
    const requiresValue = selectedItem?.requiresValue ?? true;
    onChange({
      ...condition,
      operator: newOperator,
      value: requiresValue ? condition.value : '',
    });
  };

  const handleValueChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    onChange({...condition, value: event.target.value});
  };

  const handleJsonEditorApply = (value: string | undefined) => {
    onChange({...condition, value: value ?? ''});
    setIsJsonEditorOpen(false);
  };

  return (
    <>
      <FilterRow>
        <TextInput
          id={`variable-name-${condition.id}`}
          labelText="Name"
          hideLabel
          placeholder="Variable name"
          value={condition.name}
          onChange={handleNameChange}
          onBlur={onBlur}
          invalid={!!errors.name}
          invalidText={errors.name}
          size="sm"
          autoComplete="off"
          data-testid={`variable-filter-name-${condition.id}`}
        />
        <ConditionDropdownContainer>
          <Dropdown
            id={`variable-operator-${condition.id}`}
            titleText="Operator"
            hideLabel
            label="Select condition"
            items={VARIABLE_FILTER_OPERATORS}
            itemToString={(item) => item?.label ?? ''}
            selectedItem={selectedOperator ?? null}
            onChange={({selectedItem}) => handleOperatorChange(selectedItem)}
            size="sm"
            direction={rowIndex < 2 ? 'bottom' : 'top'}
            data-testid={`variable-filter-operator-${condition.id}`}
          />
        </ConditionDropdownContainer>
        <ValueFieldContainer>
          {isValueRequired && (
            <IconTextInput
              id={`variable-value-${condition.id}`}
              labelText="Value"
              hideLabel
              placeholder={getValuePlaceholder(condition.operator)}
              value={condition.value}
              onChange={handleValueChange}
              onBlur={onBlur}
              invalid={!!errors.value}
              invalidText={errors.value}
              size="sm"
              Icon={Maximize}
              buttonLabel="Open JSON editor"
              onIconClick={() => setIsJsonEditorOpen(true)}
              data-testid={`variable-filter-value-${condition.id}`}
            />
          )}
        </ValueFieldContainer>
        <DeleteButton
          kind="ghost"
          size="sm"
          label="Remove condition"
          align="top-right"
          onClick={onDelete}
          data-testid={`delete-variable-filter-${condition.id}`}
          $hidden={isDeleteHidden}
        >
          <Close />
        </DeleteButton>
      </FilterRow>

      {isJsonEditorOpen &&
        createPortal(
          <JSONEditorModal
            isVisible={isJsonEditorOpen}
            title="Edit Variable Value"
            value={condition.value}
            onClose={() => setIsJsonEditorOpen(false)}
            onApply={handleJsonEditorApply}
          />,
          document.body,
        )}
    </>
  );
};

export {VariableFilterRow};
