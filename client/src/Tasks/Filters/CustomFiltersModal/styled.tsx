/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rem} from '@carbon/elements';
import {
  ModalFooter as BaseModalFooter,
  Toggle as BaseToggle,
  FormGroup as BaseFormGroup,
  DatePicker as BaseDatePicker,
} from '@carbon/react';
import styled from 'styled-components';

const ModalFooter = styled(BaseModalFooter)`
  display: grid;
  grid-template-columns: 25% 25% 25% 25%;

  & > :nth-child(1) {
    grid-column-start: 1;
  }

  & > :nth-child(2) {
    grid-column-start: 3;
  }

  & > :nth-child(3) {
    grid-column-start: 4;
  }
`;

const TwoColumnGrid = styled.div`
  width: 100%;
  display: grid;
  grid-template-columns: auto auto;
  grid-column-gap: var(--cds-spacing-03);
  grid-row-gap: var(--cds-spacing-06);
  align-items: flex-end;

  & > * {
    width: 100%;
    max-width: ${rem(288)};
  }

  & > .second-column {
    margin-right: calc(40px + var(--cds-spacing-03));
    justify-self: flex-end;
  }
`;

const VariablesGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(auto, ${rem(288)}) auto 40px;
  grid-column-gap: var(--cds-spacing-03);
  grid-row-gap: var(--cds-spacing-05);
  align-items: flex-end;

  &:not(:empty) {
    margin-bottom: var(--cds-spacing-03);
  }
`;

const Toggle = styled(BaseToggle)`
  grid-column-start: span 2;
`;

const VariableFormGroup = styled(BaseFormGroup)`
  grid-column-start: span 2;
  max-width: unset;
`;

const DateRangeFormGroup = styled(BaseFormGroup)`
  display: inline-flex;
`;

const DatePicker = styled(BaseDatePicker)`
  &,
  & input.cds--date-picker__input.flatpickr-input {
    inline-size: ${rem(143.5)};
  }

  &:first-of-type {
    margin-inline-end: ${rem(1)};
  }
`;

export {
  ModalFooter,
  TwoColumnGrid,
  VariablesGrid,
  Toggle,
  VariableFormGroup,
  DateRangeFormGroup,
  DatePicker,
};
