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
import {Form} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import arrayMutators from 'final-form-arrays';
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

type FormValues = {
  conditions: DraftCondition[];
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
  const [initialDraftConditions] = useState<DraftCondition[]>(() =>
    initialConditions.length > 0
      ? initialConditions.map((c) => ({...c, id: crypto.randomUUID()}))
      : [createDraft()],
  );

  const handleSubmit = (
    values: FormValues,
  ): {conditions: RowErrors[]} | undefined => {
    const conditionErrors = values.conditions.map(validateCondition);
    if (conditionErrors.some(hasErrors)) {
      return {conditions: conditionErrors};
    }
    onApply(
      values.conditions.map(
        ({id: _id, operator, name, value}): VariableCondition => {
          if (operator === 'exists' || operator === 'doesNotExist') {
            return {name, operator, value: ''};
          }
          return {name, operator, value: value ?? ''};
        },
      ),
    );
    return undefined;
  };

  return (
    <Form<FormValues>
      onSubmit={handleSubmit}
      initialValues={{conditions: initialDraftConditions}}
      mutators={{...arrayMutators}}
    >
      {({handleSubmit: submitForm}) => (
        <Modal
          open={isOpen}
          modalHeading="Filter by Variable"
          primaryButtonText="Apply"
          secondaryButtonText="Cancel"
          onRequestSubmit={submitForm}
          onRequestClose={onClose}
          onSecondarySubmit={onClose}
          preventCloseOnClickOutside
          size="md"
        >
          <Stack gap={5}>
            <Description>
              Define one or more conditions to filter process instances by
              variable values. All conditions are combined with AND logic.
            </Description>
            <FieldArray<DraftCondition> name="conditions">
              {({fields}) => (
                <Stack gap={4}>
                  {fields.map((fieldName, index) => (
                    <VariableFilterRow
                      key={fieldName}
                      fieldName={fieldName}
                      rowIndex={index}
                      onDelete={() => fields.remove(index)}
                      isDeleteHidden={fields.length === 1}
                    />
                  ))}
                  <Button
                    kind="ghost"
                    size="sm"
                    renderIcon={Add}
                    disabled={(fields.length ?? 0) >= MAX_CONDITIONS}
                    onClick={() => fields.push(createDraft())}
                  >
                    Add condition
                  </Button>
                </Stack>
              )}
            </FieldArray>
          </Stack>
        </Modal>
      )}
    </Form>
  );
};

export {VariableFilterModal};
