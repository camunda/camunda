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
import {
  type DraftCondition,
  type RowErrors,
  MAX_CONDITIONS,
  validateCondition,
  hasErrors,
} from './constants';
import {Modal, Description} from './styled';

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
  const [rowErrors, setRowErrors] = useState<Record<string, RowErrors>>({});
  const [failedOnLastSubmit, setFailedOnLastSubmit] = useState<Set<string>>(
    new Set(),
  );

  const handleAddCondition = () => {
    if (draftConditions.length >= MAX_CONDITIONS) {
      return;
    }
    setDraftConditions((prev) => [...prev, createDraft()]);
  };

  const handleConditionChange = (updated: DraftCondition) => {
    setDraftConditions((prev) =>
      prev.map((condition) =>
        condition.id === updated.id ? updated : condition,
      ),
    );
    if (failedOnLastSubmit.has(updated.id)) {
      const errors = validateCondition(updated);
      setRowErrors((prev) => ({...prev, [updated.id]: errors}));
    }
  };

  const handleConditionDelete = (id: string) => {
    setDraftConditions((prev) =>
      prev.filter((condition) => condition.id !== id),
    );
    setRowErrors((prev) => {
      const next = {...prev};
      delete next[id];
      return next;
    });
    setFailedOnLastSubmit((prev) => {
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
  };

  const handleApply = () => {
    const newErrors: Record<string, RowErrors> = {};
    const newFailed = new Set<string>();

    for (const condition of draftConditions) {
      const errors = validateCondition(condition);
      if (hasErrors(errors)) {
        newErrors[condition.id] = errors;
        newFailed.add(condition.id);
      }
    }

    if (Object.keys(newErrors).length > 0) {
      setRowErrors(newErrors);
      setFailedOnLastSubmit(newFailed);
      return;
    }

    onApply(draftConditions.map(({id: _id, ...rest}) => rest));
  };

  const handleFieldBlur = (id: string) => {
    if (!failedOnLastSubmit.has(id)) {
      return;
    }
    const condition = draftConditions.find((condition) => condition.id === id);
    if (!condition) {
      return;
    }
    const errors = validateCondition(condition);
    setRowErrors((prev) => ({...prev, [id]: errors}));
  };

  return (
    <Modal
      open={isOpen}
      modalHeading="Filter by Variable"
      primaryButtonText="Apply"
      secondaryButtonText="Cancel"
      onRequestSubmit={handleApply}
      onRequestClose={onClose}
      onSecondarySubmit={onClose}
      preventCloseOnClickOutside
      size="md"
    >
      <Stack gap={5}>
        <Description>
          Define one or more conditions to filter process instances by variable
          values. All conditions are combined with AND logic.
        </Description>
        <Stack gap={4}>
          {draftConditions.map((condition, index) => (
            <VariableFilterRow
              key={condition.id}
              condition={condition}
              onChange={handleConditionChange}
              onDelete={() => handleConditionDelete(condition.id)}
              isDeleteHidden={draftConditions.length === 1}
              rowIndex={index}
              errors={rowErrors[condition.id] ?? {}}
              onBlur={() => handleFieldBlur(condition.id)}
            />
          ))}
        </Stack>
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
