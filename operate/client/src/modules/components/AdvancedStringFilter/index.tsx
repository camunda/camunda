/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
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
  const {filterOperator, filterValue} = useMemo(() => {
    const filter = splitEncodedFilterOperation(input.value ?? '');
    return {filterOperator: filter?.operator, filterValue: filter?.value};
  }, [input.value]);

  // The selected operator is stored and synced locally to be able to change it without a value.
  // Only with a selected value, it gets synced to the form state and URL.
  const [selectedOpator, setSelectedOperator] = useState(
    filterOperator ?? '$eq',
  );
  useEffect(() => {
    if (filterOperator && filterOperator !== selectedOpator) {
      setSelectedOperator(filterOperator);
    }
  }, [filterOperator, selectedOpator]);

  const handleOperatorChange = (
    newOperator: AdvancedStringFilterOperator | null,
  ) => {
    if (newOperator === null) {
      return;
    }
    setSelectedOperator(newOperator);
    if (filterValue) {
      input.onChange(encodeFilterOperation(newOperator, filterValue));
    }
  };

  const handleValueChange = (newValue: string) => {
    if (newValue === '') {
      input.onChange(undefined);
      return;
    }
    input.onChange(encodeFilterOperation(selectedOpator, newValue));
  };

  return (
    <Container>
      <Label htmlFor={input.name}>{label}</Label>
      <Dropdown<AdvancedStringFilterOperator>
        id={`${input.name}.operator`}
        size="sm"
        titleText={`${label} filter type`}
        hideLabel
        label="Select operator"
        items={selectableOperators}
        itemToString={(item) => (item ? OPERATOR_CONFIG[item].label : '')}
        selectedItem={selectedOpator}
        onChange={({selectedItem}) => handleOperatorChange(selectedItem)}
      />
      <TextInputField
        name={input.name}
        id={input.name}
        size="sm"
        labelText=""
        hideLabel
        placeholder={OPERATOR_CONFIG[selectedOpator].placeholder}
        value={filterValue ?? ''}
        onChange={(event) => handleValueChange(event.target.value)}
        onBlur={input.onBlur}
        autoFocus
      />
    </Container>
  );
};

export {AdvancedStringFilter};
