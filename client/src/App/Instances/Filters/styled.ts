/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {errorBorders} from 'modules/theme/interactions';
import Panel from 'modules/components/Panel';
import {Input as BasicTextInput} from 'modules/components/Input';
import BasicTextarea from 'modules/components/Textarea';
import BasicSelect from 'modules/components/Select';
import {ValidationTextInput as BasicValidationTextInput} from 'modules/components/ValidationTextInput';
import BasicCheckboxGroup from './CheckboxGroup';
import {VariableFilterInput as BasicVariableFilterInput} from './VariableFilterInput';

const Filters = styled.div`
  padding: 20px 21px 0 19px;
`;

const Field = styled.div`
  padding: 10px 0;

  &:first-child {
    padding-top: 0;
  }
`;

const widthStyle = css`
  width: 280px;
`;

const Select = styled(BasicSelect)`
  ${widthStyle};
`;

const Textarea = styled(BasicTextarea)`
  ${widthStyle};
  ${errorBorders};

  min-height: 52px;
  max-height: 100px;

  resize: vertical;
`;

const TextInput = styled(BasicTextInput)`
  ${widthStyle};
`;

const ValidationTextInput = styled(BasicValidationTextInput)`
  ${widthStyle}
`;

const CheckboxGroup = styled(BasicCheckboxGroup)`
  ${widthStyle};
`;

const VariableFilterInput = styled(BasicVariableFilterInput)`
  ${widthStyle};
`;

const ResetButtonContainer = styled(Panel.Footer)`
  ${({theme}) => {
    const shadow = theme.shadows.filters.resetButtonContainer;

    return css`
      display: flex;
      justify-content: center;
      min-height: 39px;
      max-height: 39px;
      width: 100%;
      box-shadow: ${shadow};
      border-radius: 0;
      margin-top: auto;
      position: sticky;
      bottom: 0;
    `;
  }}
`;

export {
  Filters,
  Field,
  Select,
  Textarea,
  TextInput,
  ValidationTextInput,
  CheckboxGroup,
  VariableFilterInput,
  ResetButtonContainer,
};
