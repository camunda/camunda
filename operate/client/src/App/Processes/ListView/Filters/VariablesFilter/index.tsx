/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {observer} from 'mobx-react';
import {createPortal} from 'react-dom';
import {Edit} from '@carbon/react/icons';
import {IconTextInput} from 'modules/components/IconInput';
import {VariablesFilterModal} from './VariablesFilterModal';
import {type VariableFilterCondition, VARIABLE_FILTER_OPERATORS} from './constants';

interface Props {
  isModalOpen: boolean;
  onModalOpen: () => void;
  onModalClose: () => void;
}

/**
 * VariablesFilter - Component for displaying and managing multiple variable filters.
 *
 * This component displays the applied variable filters in a text input field and
 * provides a button to open the modal editor for adding/editing filters.
 * - Single filter: shows the condition (e.g., "status equals active")
 * - Multiple filters: shows count (e.g., "2 conditions applied")
 *
 * Integration with form state:
 * TODO: Integrate with react-final-form to persist filter state
 * The conditions should be stored in the form state under a key like 'variableFilters'
 * and converted to the API format when submitting the search request.
 *
 * API format conversion example:
 * ```typescript
 * const convertToApiFormat = (conditions: VariableFilterCondition[]) => {
 *   return conditions.map(c => ({
 *     name: c.name,
 *     value: convertOperatorToApiFormat(c.operator, c.value)
 *   }));
 * };
 * ```
 */
const VariablesFilter: React.FC<Props> = observer(
  ({isModalOpen, onModalOpen, onModalClose}) => {
    // TODO: Replace with form state from react-final-form
    // const form = useForm();
    // const formState = useFormState();
    // const initialConditions = formState.values?.variableFilters ?? [];
    const [conditions, setConditions] = useState<VariableFilterCondition[]>([]);

    const handleApply = (newConditions: VariableFilterCondition[]) => {
      setConditions(newConditions);
      // TODO: Update form state when integrating with actual filtering
      // form.change('variableFilters', newConditions);
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
          id="variables-filter"
          labelText="Variables"
          value={getDisplayValue()}
          title={getDisplayValue()}
          placeholder="Add variable filters"
          size="sm"
          buttonLabel="Open variable editor"
          onIconClick={onModalOpen}
          onClick={onModalOpen}
          data-testid="variables-filter-input"
        />

        {createPortal(
          <VariablesFilterModal
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

export {VariablesFilter};
export {VariablesFilterModal} from './VariablesFilterModal';
export {VariableFilterRow} from './VariableFilterRow';
export type {VariableFilterCondition, VariableFilterOperator} from './constants';
export {VARIABLE_FILTER_OPERATORS, MOCK_VARIABLE_NAMES} from './constants';
