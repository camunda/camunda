/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Field, useForm} from 'react-final-form';
import {Dropdown} from '@carbon/react';
import {TextInputField} from 'modules/components/TextInputField';
import {
  BUSINESS_ID_FILTER_OPERATORS,
  DEFAULT_BUSINESS_ID_OPERATOR,
  resolveBusinessIdOperator,
} from './constants';
import * as Styled from './styled';

/**
 * Inline operator + value row for filtering by Business ID.
 *
 * Reads/writes two URL params via the surrounding Final Form:
 *   businessId          – the value (omitted when operator is `equals`
 *                         with an empty value, i.e. filter inactive)
 *   businessIdOperator  – the operator id (omitted when operator is
 *                         `equals`, so legacy `?businessId=foo` URLs from
 *                         before this filter shipped continue to work)
 *
 * The value field is hidden for `exists` and `doesNotExist`. When the user
 * switches operator to a value-less one, any typed value is intentionally
 * preserved in the URL and form state so switching back restores it; the
 * value is just visually hidden and ignored by buildBusinessIdFilterValue.
 */
const BusinessIdFilter: React.FC = () => {
  const form = useForm();

  return (
    <Field name="businessIdOperator">
      {({input: operatorInput}) => {
        const rawValue =
          typeof operatorInput.value === 'string'
            ? operatorInput.value
            : undefined;
        const operatorId = resolveBusinessIdOperator(rawValue);
        const operatorConfig =
          BUSINESS_ID_FILTER_OPERATORS.find((op) => op.id === operatorId) ??
          BUSINESS_ID_FILTER_OPERATORS[0];
        const isValueRequired = operatorConfig.requiresValue;

        const RowComponent = isValueRequired
          ? Styled.FilterRow
          : Styled.OperatorOnlyRow;

        return (
          <RowComponent>
            <Dropdown<(typeof BUSINESS_ID_FILTER_OPERATORS)[number]>
              id="businessIdOperator"
              titleText="Business ID condition"
              hideLabel
              label="Select condition"
              size="sm"
              items={BUSINESS_ID_FILTER_OPERATORS}
              itemToString={(item) => item?.label ?? ''}
              selectedItem={operatorConfig}
              onChange={({selectedItem}) => {
                const nextId = selectedItem?.id ?? DEFAULT_BUSINESS_ID_OPERATOR;
                // For the default operator, omit the param so legacy
                // `?businessId=foo` URLs round-trip cleanly.
                operatorInput.onChange(
                  nextId === DEFAULT_BUSINESS_ID_OPERATOR ? undefined : nextId,
                );
                // The typed `businessId` value is intentionally NOT cleared
                // when switching to `exists` / `doesNotExist`: it stays in
                // the URL and form state so that switching back to a
                // value-required operator restores what the user typed. The
                // value is hidden visually (the input does not render) and
                // ignored by buildBusinessIdFilterValue.
                form.submit();
              }}
              data-testid="business-id-operator"
            />
            {isValueRequired ? (
              <Field name="businessId">
                {({input: valueInput}) => (
                  <TextInputField
                    {...valueInput}
                    id="businessId"
                    size="sm"
                    labelText="Business ID"
                    hideLabel
                    placeholder="Business ID"
                    autoFocus
                    data-testid="business-id-value"
                  />
                )}
              </Field>
            ) : null}
          </RowComponent>
        );
      }}
    </Field>
  );
};

export {BusinessIdFilter};
