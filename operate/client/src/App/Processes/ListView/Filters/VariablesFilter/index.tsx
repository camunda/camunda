/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {observer} from 'mobx-react';
import {Button} from '@carbon/react';
import {Edit} from '@carbon/react/icons';
import {Title} from 'modules/components/FiltersPanel/styled';
import {
  variableFilterStore,
  type VariableCondition,
} from 'modules/stores/variableFilter';
import {VARIABLE_FILTER_OPERATORS} from './constants';
import {VariableFilterModal} from './VariableFilterModal';
import {ConditionList, ConditionItem} from './styled';

const getConditionLabel = (condition: VariableCondition): string => {
  const config = VARIABLE_FILTER_OPERATORS.find(
    (op) => op.id === condition.operator,
  );
  return config?.requiresValue
    ? `${condition.name} ${config.label} ${condition.value}`
    : `${condition.name} ${config?.label ?? condition.operator}`;
};

const VariableFilter: React.FC = observer(() => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const {conditions} = variableFilterStore;

  const handleApply = (newConditions: VariableCondition[]) => {
    variableFilterStore.setConditions(newConditions);
    setIsModalOpen(false);
  };

  return (
    <>
      <Title>Variables</Title>
      {conditions.length > 0 && (
        <ConditionList aria-label="Active variable filters">
          {conditions.map((condition) => (
            <ConditionItem
              key={`${condition.name}-${condition.operator}-${condition.value}`}
            >
              {getConditionLabel(condition)}
            </ConditionItem>
          ))}
        </ConditionList>
      )}
      <Button
        kind="ghost"
        size="sm"
        renderIcon={Edit}
        onClick={() => setIsModalOpen(true)}
        data-testid="open-variable-filter-modal"
      >
        {conditions.length === 0 ? 'Add conditions' : 'Edit conditions'}
      </Button>

      <VariableFilterModal
        key={isModalOpen ? 'open' : 'closed'}
        isOpen={isModalOpen}
        initialConditions={conditions}
        onApply={handleApply}
        onClose={() => setIsModalOpen(false)}
      />
    </>
  );
});

export {VariableFilter};
