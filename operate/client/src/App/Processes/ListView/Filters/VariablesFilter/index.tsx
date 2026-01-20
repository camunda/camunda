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
import {Button, Tag} from '@carbon/react';
import {Edit} from '@carbon/react/icons';
import {Title} from 'modules/components/FiltersPanel/styled';
import {VariablesFilterModal} from './VariablesFilterModal';
import {type VariableFilterCondition} from './constants';
import styled from 'styled-components';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
`;

const TagsContainer = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: var(--cds-spacing-02);
`;

const EditButtonContainer = styled.div`
  display: flex;
  align-items: center;
`;

interface Props {
  isModalOpen: boolean;
  onModalOpen: () => void;
  onModalClose: () => void;
}

/**
 * VariablesFilter - Component for displaying and managing multiple variable filters.
 *
 * This component displays the applied variable filters as tags and provides
 * a button to open the modal editor for adding/editing filters.
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
      if (condition.operator === 'isNull') {
        return `${condition.name} is null`;
      }
      if (condition.operator === 'isNotNull') {
        return `${condition.name} is not null`;
      }
      return `${condition.name} ${condition.operator} ${condition.value}`;
    };

    return (
      <>
        <Title>Variables</Title>
        <Container>
          {conditions.length > 0 ? (
            <TagsContainer>
              {conditions.map((condition) => (
                <Tag
                  key={condition.id}
                  type="gray"
                  size="sm"
                  data-testid={`variable-filter-tag-${condition.id}`}
                >
                  {getConditionLabel(condition)}
                </Tag>
              ))}
            </TagsContainer>
          ) : null}
          <EditButtonContainer>
            <Button
              kind="ghost"
              size="sm"
              renderIcon={Edit}
              onClick={onModalOpen}
              data-testid="edit-variables-filter-button"
            >
              {conditions.length > 0 ? 'Edit filters' : 'Add filters'}
            </Button>
          </EditButtonContainer>
        </Container>

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
