/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, Modal, Stack} from '@carbon/react';
import {Add} from '@carbon/react/icons';
import {Form} from 'react-final-form';
import {FieldArray} from 'react-final-form-arrays';
import arrayMutators from 'final-form-arrays';
import {isValidJSON} from 'modules/utils';
import {VariableFilterRow} from './VariableFilterRow';
import type {VariableCondition} from 'modules/stores/variableFilter';
import type {DraftCondition} from './constants';
import {Description, ModalContent} from './styled';

const MAX_CONDITIONS = 5;

type RowErrors = {
  name?: string;
  value?: string;
};

const validateCondition = (condition: DraftCondition): RowErrors => {
  const errors: RowErrors = {};

  if (!condition.name.trim()) {
    errors.name = 'Variable name is required';
  }

  if (
    condition.operator !== 'exists' &&
    condition.operator !== 'doesNotExist'
  ) {
    if (!condition.value.trim()) {
      errors.value = 'Value is required';
    } else if (condition.operator === 'oneOf') {
      let parsed: unknown;
      try {
        parsed = JSON.parse(condition.value);
      } catch {
        // handled below
      }
      if (!Array.isArray(parsed)) {
        errors.value = 'Value must be a JSON array (e.g. ["val1", "val2"])';
      }
    } else if (
      condition.operator !== 'contains' &&
      !isValidJSON(condition.value)
    ) {
      errors.value = 'Value must be valid JSON';
    }
  }

  return errors;
};

const hasErrors = (errors: RowErrors) => Object.keys(errors).length > 0;

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
          <ModalContent>
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
          </ModalContent>
        </Modal>
      )}
    </Form>
  );
};

export {VariableFilterModal};
