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
import {
  type VariableFilterCondition,
  type VariableFilterOperator,
  VARIABLE_FILTER_OPERATORS,
} from './constants';
import * as Styled from './styled';

interface Props {
  condition: VariableFilterCondition;
  onChange: (condition: VariableFilterCondition) => void;
  onDelete: () => void;
  isDeleteDisabled: boolean;
  rowIndex: number;
}

const VariableFilterRow: React.FC<Props> = ({
  condition,
  onChange,
  onDelete,
  isDeleteDisabled,
  rowIndex,
}) => {
  const [isJsonEditorOpen, setIsJsonEditorOpen] = useState(false);

  const selectedOperator = VARIABLE_FILTER_OPERATORS.find(
    (op) => op.id === condition.operator,
  );
  const isValueRequired =
    selectedOperator?.id !== 'exists' &&
    selectedOperator?.id !== 'doesNotExist';

  const handleNameChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    onChange({
      ...condition,
      name: event.target.value,
    });
  };

  const handleOperatorChange = (selectedItem: VariableFilterOperator | null) => {
    const newOperator = selectedItem ?? 'equals';
    const operatorConfig = VARIABLE_FILTER_OPERATORS.find(
      (op) => op.id === newOperator,
    );

    onChange({
      ...condition,
      operator: newOperator,
      value: operatorConfig?.requiresValue ? condition.value : '',
    });
  };

  const handleValueChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    onChange({
      ...condition,
      value: event.target.value,
    });
  };

  const handleJsonEditorApply = (value: string | undefined) => {
    onChange({
      ...condition,
      value: value ?? '',
    });
    setIsJsonEditorOpen(false);
  };

  const getValuePlaceholder = (): string => {
    switch (selectedOperator?.id) {
      case 'equals':
      case 'notEqual':
        return 'value in JSON format';
      case 'contains':
        return '"search text"';
      case 'oneOf':
        return '["val1", "val2"]';
      default:
        return 'Value, in JSON format';
    }
  };

  return (
    <>
      <Styled.FilterRow>
        <TextInput
          id={`variable-name-${condition.id}`}
          labelText="Name"
          hideLabel
          placeholder="Variable name"
          value={condition.name}
          onChange={handleNameChange}
          size="sm"
          data-testid={`variable-filter-name-${condition.id}`}
        />
        <Styled.ConditionDropdownContainer>
          <Dropdown
            id={`variable-operator-${condition.id}`}
            titleText=""
            label="Select condition"
            items={VARIABLE_FILTER_OPERATORS}
            itemToString={(item) => item?.label ?? ''}
            selectedItem={selectedOperator ?? null}
            onChange={({selectedItem}) =>
              handleOperatorChange(selectedItem?.id ?? null)
            }
            size="sm"
            direction={rowIndex < 2 ? 'bottom' : 'top'}
            data-testid={`variable-filter-operator-${condition.id}`}
          />
        </Styled.ConditionDropdownContainer>
        <Styled.ValueFieldContainer>
          {isValueRequired && (
            <IconTextInput
              id={`variable-value-${condition.id}`}
              labelText="Value"
              hideLabel
              placeholder={getValuePlaceholder()}
              value={condition.value}
              onChange={handleValueChange}
              size="sm"
              Icon={Maximize}
              buttonLabel="Open JSON editor"
              onIconClick={() => setIsJsonEditorOpen(true)}
              data-testid={`variable-filter-value-${condition.id}`}
            />
          )}
        </Styled.ValueFieldContainer>
        {!isDeleteDisabled && (
          <Styled.DeleteButton
            kind="ghost"
            size="sm"
            label="Remove condition"
            align="top-right"
            onClick={onDelete}
            data-testid={`delete-variable-filter-${condition.id}`}
          >
            <Close />
          </Styled.DeleteButton>
        )}
      </Styled.FilterRow>

      {/* JSON Editor Modal for editing value */}
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
