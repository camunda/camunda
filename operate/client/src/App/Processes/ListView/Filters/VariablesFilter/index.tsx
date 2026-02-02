/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {createPortal} from 'react-dom';
import {Edit} from '@carbon/react/icons';
import {IconTextInput} from 'modules/components/IconInput';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {VariableFilterModal} from './VariableFilterModal';
import {type VariableFilterCondition, VARIABLE_FILTER_OPERATORS} from './constants';

interface Props {
  isModalOpen: boolean;
  onModalOpen: () => void;
  onModalClose: () => void;
}

/**
 * VariableFilter - Component for displaying and managing multiple variable filters.
 *
 * This component displays the applied variable filters in a text input field and
 * provides a button to open the modal editor for adding/editing filters.
 * - Single filter: shows the condition (e.g., "status equals active")
 * - Multiple filters: shows count (e.g., "2 conditions applied")
 *
 * The conditions are stored in variableFilterStore and automatically trigger
 * API searches when changed via MobX reactions in the ListView.
 */
const VariableFilter: React.FC<Props> = observer(
  ({isModalOpen, onModalOpen, onModalClose}) => {
    const conditions = variableFilterStore.conditions;

    const handleApply = (newConditions: VariableFilterCondition[]) => {
      // Store conditions in MobX store - this triggers the search via reaction
      variableFilterStore.setConditions(newConditions);
      onModalClose();
    };

    const getConditionLabel = (condition: VariableFilterCondition): string => {
      const operatorConfig = VARIABLE_FILTER_OPERATORS.find(
        (op) => op.id === condition.operator,
      );
      const operatorLabel = operatorConfig?.label ?? condition.operator;

      if (condition.operator === 'exists' || condition.operator === 'doesNotExist') {
        return `${condition.name} ${operatorLabel}`;
      }

      return `${condition.name} ${operatorLabel} ${condition.value}`;
    };

    const getDisplayValue = (): string => {
      if (conditions.length === 0) {
        return '';
      }
      if (conditions.length === 1) {
        return getConditionLabel(conditions[0]!);
      }
      return `${conditions.length} conditions applied`;
    };

    return (
      <>
        <IconTextInput
          Icon={Edit}
          id="variable-filter"
          labelText="Variables"
          value={getDisplayValue()}
          title={getDisplayValue()}
          placeholder="Add variable filters"
          size="sm"
          buttonLabel="Open variable editor"
          onIconClick={onModalOpen}
          onClick={onModalOpen}
          data-testid="variable-filter-input"
        />

        {createPortal(
          <VariableFilterModal
            isOpen={isModalOpen}
            onClose={onModalClose}
            onApply={handleApply}
            initialConditions={conditions}
          />,
          document.body,
        )}
      </>
    );
  },
);

export {VariableFilter};
export {VariableFilterModal} from './VariableFilterModal';
export {VariableFilterRow} from './VariableFilterRow';
export type {VariableFilterCondition, VariableFilterOperator} from './constants';
export {VARIABLE_FILTER_OPERATORS, MOCK_VARIABLE_NAMES} from './constants';
