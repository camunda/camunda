/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useCallback, useEffect} from 'react';
import {Button, Stack} from '@carbon/react';
import {Add} from '@carbon/react/icons';
import {getValidVariableValues} from 'modules/utils/filter/getValidVariableValues';
import {type VariableFilterCondition} from './constants';
import {VariableFilterRow} from './VariableFilterRow';
import * as Styled from './styled';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onApply: (conditions: VariableFilterCondition[]) => void;
  initialConditions?: VariableFilterCondition[];
}

const generateId = (): string => {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
};

const createEmptyCondition = (): VariableFilterCondition => ({
  id: generateId(),
  name: '',
  operator: 'equals',
  value: '',
});

/**
 * VariablesFilterModal - Modal for filtering process instances by multiple variables.
 *
 * This component allows users to add multiple variable filter conditions with
 * logical operators. Results will match ALL conditions (AND logic).
 *
 * API Integration Notes:
 * TODO: Replace mock data handling with actual API call to POST /v2/process-instances/search
 *
 * Expected request format when applying filters:
 * {
 *   "filter": {
 *     "variables": [
 *       { "name": "customer_id", "value": { "$eq": "123" } },
 *       { "name": "status", "value": { "$like": "*active*" } },
 *       { "name": "priority", "value": { "$in": ["high", "critical"] } },
 *       { "name": "deleted_at", "value": { "$exists": false } }
 *     ]
 *   }
 * }
 *
 * Operator to API mapping:
 * - equals        → { "$eq": value }
 * - notEqual      → { "$neq": value }
 * - contains      → { "$like": "*value*" } (auto-wrap wildcards)
 * - oneOf         → { "$in": [values] } (always array, even for single value)
 * - exists        → { "$exists": true }
 * - doesNotExist  → { "$exists": false }
 */
const VariablesFilterModal: React.FC<Props> = ({
  isOpen,
  onClose,
  onApply,
  initialConditions,
}) => {
  const [conditions, setConditions] = useState<VariableFilterCondition[]>(() =>
    initialConditions && initialConditions.length > 0
      ? initialConditions
      : [createEmptyCondition()],
  );

  useEffect(() => {
    if (isOpen) {
      setConditions(
        initialConditions && initialConditions.length > 0
          ? initialConditions
          : [createEmptyCondition()],
      );
    }
  }, [isOpen, initialConditions]);

  const handleAddCondition = useCallback(() => {
    setConditions((prev) => [...prev, createEmptyCondition()]);
  }, []);

  const handleRemoveCondition = useCallback((idToRemove: string) => {
    setConditions((prev) => prev.filter((c) => c.id !== idToRemove));
  }, []);

  const handleConditionChange = useCallback(
    (updatedCondition: VariableFilterCondition) => {
      setConditions((prev) =>
        prev.map((c) => (c.id === updatedCondition.id ? updatedCondition : c)),
      );
    },
    [],
  );

  const handleApply = () => {
    const validConditions = conditions.filter((c) => {
      if (!c.name.trim()) return false;
      if (c.operator === 'exists' || c.operator === 'doesNotExist') return true;
      return c.value.trim() !== '';
    });

    try {
      const apiConditions = validConditions.map((c) => {
        const {name, operator, value} = c;

        if (operator === 'exists') {
          return {name, value: {$exists: true}};
        }
        if (operator === 'doesNotExist') {
          return {name, value: {$exists: false}};
        }

        const parsed = (getValidVariableValues(value) ?? []).map((v) =>
          JSON.stringify(v),
        );

        if (parsed.length === 0) {
          throw new Error(`Invalid value for ${name}`);
        }

        switch (operator) {
          case 'equals':
            return {name, value: {$eq: parsed[0]}};
          case 'notEqual':
            return {name, value: {$neq: parsed[0]}};
          case 'contains':
            return {name, value: {$like: `*${parsed[0]}*`}};
          case 'oneOf':
            return {name, value: {$in: parsed}};
          default:
            return {name, value: {$eq: parsed[0]}};
        }
      });

      // TODO: Send apiConditions to actual API
      console.log('API conditions:', apiConditions);
      onApply(validConditions);
      onClose();
    } catch (error) {
      // TODO: Show validation error to user with notification
      console.error('Invalid filter values:', error);
    }
  };

  const handleCancel = () => {
    onClose();
  };

  const isApplyDisabled = conditions.every(
    (c) =>
      c.name.trim() === '' ||
      (c.operator !== 'exists' &&
        c.operator !== 'doesNotExist' &&
        c.value.trim() === ''),
  );

  return (
    <Styled.Modal
      open={isOpen}
      size="md"
      modalHeading="Filter by variables"
      primaryButtonText="Apply"
      secondaryButtonText="Cancel"
      onRequestClose={handleCancel}
      onRequestSubmit={handleApply}
      primaryButtonDisabled={isApplyDisabled}
      preventCloseOnClickOutside
      selectorsFloatingMenus={['.cds--modal *']}
      data-testid="variables-filter-modal"
    >
      <Stack gap={6}>
        <Styled.Description>
          Results will match all conditions.
        </Styled.Description>

        <Styled.FilterRowsContainer gap={5}>
          {conditions.map((condition, index) => (
            <VariableFilterRow
              key={condition.id}
              condition={condition}
              onChange={handleConditionChange}
              onDelete={() => handleRemoveCondition(condition.id)}
              isDeleteDisabled={conditions.length === 1}
              rowIndex={index}
            />
          ))}
        </Styled.FilterRowsContainer>

        <Button
          kind="ghost"
          size="sm"
          renderIcon={Add}
          onClick={handleAddCondition}
          data-testid="add-variable-filter-button"
        >
          Add
        </Button>
      </Stack>
    </Styled.Modal>
  );
};

export {VariablesFilterModal};
