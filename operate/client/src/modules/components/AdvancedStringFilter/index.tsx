/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Dropdown} from '@carbon/react';
import {Field, type FieldInputProps} from 'react-final-form';
import {TextInputField} from 'modules/components/TextInputField';
import {
  encodeFilterOperation,
  splitEncodedFilterOperation,
  type AdvancedStringFilterOperator,
} from 'modules/utils/filter/advancedStringFilter';
import {Container, Label} from './styled';

const OPERATOR_CONFIG: Record<
  AdvancedStringFilterOperator,
  {label: string; placeholder?: string}
> = {
  $eq: {label: 'equals'},
  $neq: {label: 'does not equal'},
  $like: {label: 'contains'},
  $in: {label: 'is one of', placeholder: 'space or comma separated'},
  $notIn: {label: 'is not one of', placeholder: 'space or comma separated'},
  $exists: {label: 'exists', placeholder: 'true or false'},
};

type Props = {
  name: string;
  label: string;
  selectableOperators: AdvancedStringFilterOperator[];
};

const AdvancedStringFilter: React.FC<Props> = ({
  name,
  label,
  selectableOperators,
}) => {
  return (
    <Field<string | undefined> name={name}>
      {({input}) => (
        <AdvancedStringFilterField
          input={input}
          label={label}
          selectableOperators={selectableOperators}
        />
      )}
    </Field>
  );
};

type FieldProps = {
  input: FieldInputProps<string | undefined, HTMLElement>;
  label: string;
  selectableOperators: AdvancedStringFilterOperator[];
};

const AdvancedStringFilterField: React.FC<FieldProps> = ({
  input,
  label,
  selectableOperators,
}) => {
  const filter = splitEncodedFilterOperation(input.value ?? '');

  const [fallbackOperator, setFallbackOperator] =
    useState<AdvancedStringFilterOperator>('$eq');
  const selectedOperator = filter?.operator ?? fallbackOperator;

  const handleOperatorChange = (
    newOperator: AdvancedStringFilterOperator | null,
  ) => {
    if (newOperator === null) {
      return;
    }
    setFallbackOperator(newOperator);
    if (filter?.value) {
      input.onChange(encodeFilterOperation(newOperator, filter.value));
    }
  };

  const handleValueChange = (newValue: string) => {
    if (newValue === '') {
      setFallbackOperator(selectedOperator);
      input.onChange(undefined);
      return;
    }
    input.onChange(encodeFilterOperation(selectedOperator, newValue));
  };

  return (
    <Container>
      <Label htmlFor={input.name}>{label}</Label>
      <Dropdown<AdvancedStringFilterOperator>
        id={`${input.name}.operator`}
        size="sm"
        direction="top"
        titleText={`${label} filter type`}
        hideLabel
        label="Select operator"
        items={selectableOperators}
        itemToString={(item) => (item ? OPERATOR_CONFIG[item].label : '')}
        selectedItem={selectedOperator}
        onChange={({selectedItem}) => handleOperatorChange(selectedItem)}
      />
      <TextInputField
        name={input.name}
        id={input.name}
        size="sm"
        labelText=""
        hideLabel
        placeholder={OPERATOR_CONFIG[selectedOperator].placeholder}
        value={filter?.value ?? ''}
        onChange={(event) => handleValueChange(event.target.value)}
        onBlur={input.onBlur}
        autoFocus
      />
    </Container>
  );
};

export {AdvancedStringFilter};
