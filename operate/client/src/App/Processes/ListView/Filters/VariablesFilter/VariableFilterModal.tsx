/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, Stack} from '@carbon/react';
import {Add} from '@carbon/react/icons';
import {VariableFilterRow} from './VariableFilterRow';
import type {VariableCondition} from 'modules/stores/variableFilter';
import {type DraftCondition, MAX_CONDITIONS} from './constants';
import {Modal, FilterRowsContainer, Description} from './styled';

type Props = {
  isOpen: boolean;
  initialConditions: VariableCondition[];
  onApply: (conditions: VariableCondition[]) => void;
  onClose: () => void;
};

const createDraft = (): DraftCondition => ({
  id: crypto.randomUUID(),
  name: '',
  operator: 'equals',
  value: '',
});

const isConditionValid = (c: DraftCondition): boolean => {
  if (!c.name.trim()) {
    return false;
  }
  if (c.operator === 'exists' || c.operator === 'doesNotExist') {
    return true;
  }
  return c.value.trim() !== '';
};

const VariableFilterModal: React.FC<Props> = ({
  isOpen,
  initialConditions,
  onApply,
  onClose,
}) => {
  const [draftConditions, setDraftConditions] = useState<DraftCondition[]>(
    () =>
      initialConditions.length > 0
        ? initialConditions.map((c) => ({...c, id: crypto.randomUUID()}))
        : [createDraft()],
  );

  const handleAddCondition = () => {
    if (draftConditions.length >= MAX_CONDITIONS) {
      return;
    }
    setDraftConditions((prev) => [...prev, createDraft()]);
  };

  const handleConditionChange = (updated: DraftCondition) => {
    setDraftConditions((prev) =>
      prev.map((c) => (c.id === updated.id ? updated : c)),
    );
  };

  const handleConditionDelete = (id: string) => {
    setDraftConditions((prev) => prev.filter((c) => c.id !== id));
  };

  const handleApply = () => {
    const valid = draftConditions
      .filter(isConditionValid)
      .map(({id: _id, ...rest}) => rest);
    onApply(valid);
  };

  const isApplyDisabled = !draftConditions.some(isConditionValid);

  return (
    <Modal
      open={isOpen}
      modalHeading="Filter by Variable"
      primaryButtonText="Apply"
      secondaryButtonText="Cancel"
      onRequestSubmit={handleApply}
      onRequestClose={onClose}
      onSecondarySubmit={onClose}
      primaryButtonDisabled={isApplyDisabled}
      preventCloseOnClickOutside
      size="md"
    >
      <Stack gap={5}>
        <Description>
          Define one or more conditions to filter process instances by variable
          values. All conditions are combined with AND logic.
        </Description>
        <FilterRowsContainer gap={4}>
          {draftConditions.map((condition, index) => (
            <VariableFilterRow
              key={condition.id}
              condition={condition}
              onChange={handleConditionChange}
              onDelete={() => handleConditionDelete(condition.id)}
              isDeleteHidden={draftConditions.length === 1}
              rowIndex={index}
            />
          ))}
        </FilterRowsContainer>
        <Button
          kind="ghost"
          size="sm"
          renderIcon={Add}
          disabled={draftConditions.length >= MAX_CONDITIONS}
          onClick={handleAddCondition}
        >
          Add condition
        </Button>
      </Stack>
    </Modal>
  );
};

export {VariableFilterModal};
