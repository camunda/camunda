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
 * VariableFilterModal - Modal for filtering process instances by multiple variables.
 *
 * This component allows users to add multiple variable filter conditions with
 * logical operators. Results will match ALL conditions (AND logic).
 *
 * The conditions are passed to the parent component via onApply, which stores them
 * in variableFilterStore. The store triggers the V2 API search automatically via
 * MobX reactions.
 *
 * API format conversion (handled by convertVariableConditionsToApiFormat):
 * - equals        → { "$eq": value }
 * - notEqual      → { "$neq": value }
 * - contains      → { "$like": "*value*" }
 * - oneOf         → { "$in": [values] }
 * - exists        → { "$exists": true }
 * - doesNotExist  → { "$exists": false }
 */
const VariableFilterModal: React.FC<Props> = ({
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
    // Filter out invalid conditions before passing to parent
    const validConditions = conditions.filter((c) => {
      if (!c.name.trim()) {
        return false;
      }
      if (c.operator === 'exists' || c.operator === 'doesNotExist') {
        return true;
      }
      return c.value.trim() !== '';
    });

    // Pass valid conditions to parent - API format conversion happens in the hook
    onApply(validConditions);
    onClose();
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
      data-testid="variable-filter-modal"
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

export {VariableFilterModal};
