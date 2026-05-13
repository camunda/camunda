/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useNavigate, useLocation} from 'react-router-dom';
import truncate from 'lodash/truncate';
import {Button} from '@carbon/react';
import {Edit} from '@carbon/react/icons';
import {Paths} from 'modules/Routes';
import {Title} from 'modules/components/FiltersPanel/styled';
import {
  variableFilterStore,
  type VariableCondition,
} from 'modules/stores/variableFilter';
import {VARIABLE_FILTER_OPERATORS} from './constants';
import {ConditionList, ConditionItem} from './styled';

const getConditionLabel = (condition: VariableCondition): string => {
  const config = VARIABLE_FILTER_OPERATORS.find(
    (op) => op.id === condition.operator,
  );
  if (!config?.requiresValue) {
    return `${condition.name} ${config?.label ?? condition.operator}`;
  }
  return `${condition.name} ${config.label} ${truncate(condition.value, {length: 50})}`;
};

const VariableFilter: React.FC = observer(() => {
  const navigate = useNavigate();
  const location = useLocation();
  const {conditions} = variableFilterStore;

  const openModal = () => {
    navigate({
      pathname: Paths.processesVariables(),
      search: location.search,
    });
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
        onClick={openModal}
        data-testid="open-variable-filter-modal"
      >
        {conditions.length === 0 ? 'Add conditions' : 'Edit conditions'}
      </Button>
    </>
  );
});

export {VariableFilter};
