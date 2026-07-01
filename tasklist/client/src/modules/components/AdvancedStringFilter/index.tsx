/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Dropdown, TextInput} from '@carbon/react';
import {Field, type FieldInputProps} from 'react-final-form';
import {useTranslation} from 'react-i18next';
import cn from 'classnames';
import {
  encodeFilterOperation,
  splitEncodedFilterOperation,
  type AdvancedStringFilterOperator,
} from 'modules/tasks/filters/advancedStringFilter';
import styles from './styles.module.scss';

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
  const {t} = useTranslation();
  const OPERATOR_CONFIG: Record<
    AdvancedStringFilterOperator,
    {label: string; placeholder?: string}
  > = {
    $eq: {label: t('customFiltersModalOperatorEquals')},
    $neq: {label: t('customFiltersModalOperatorNotEquals')},
    $like: {label: t('customFiltersModalOperatorContains')},
    $in: {
      label: t('customFiltersModalOperatorIsOneOf'),
      placeholder: t('customFiltersModalOperatorListPlaceholder'),
    },
    $notIn: {
      label: t('customFiltersModalOperatorIsNotOneOf'),
      placeholder: t('customFiltersModalOperatorListPlaceholder'),
    },
    $exists: {
      label: t('customFiltersModalOperatorExists'),
      placeholder: t('customFiltersModalOperatorExistsPlaceholder'),
    },
  };

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
    <div className={styles.container}>
      <label htmlFor={input.name} className={cn('cds--label', styles.label)}>
        {label}
      </label>
      <Dropdown<AdvancedStringFilterOperator>
        id={`${input.name}.operator`}
        size="md"
        direction="top"
        titleText={t('customFiltersModalOperatorTypeAriaLabel', {label})}
        hideLabel
        label={t('customFiltersModalOperatorTypeAriaLabel', {label})}
        items={selectableOperators}
        itemToString={(item) => (item ? OPERATOR_CONFIG[item].label : '')}
        selectedItem={selectedOperator}
        onChange={({selectedItem}) => handleOperatorChange(selectedItem)}
      />
      <TextInput
        name={input.name}
        id={input.name}
        size="md"
        labelText=""
        hideLabel
        placeholder={OPERATOR_CONFIG[selectedOperator].placeholder}
        value={filter?.value ?? ''}
        onChange={(event) => handleValueChange(event.target.value)}
        onBlur={input.onBlur}
      />
    </div>
  );
};

export {AdvancedStringFilter};
