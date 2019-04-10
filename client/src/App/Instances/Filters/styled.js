/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {themed, themeStyle} from 'modules/theme';
import Panel from 'modules/components/Panel';
import BasicExpandButton from 'modules/components/ExpandButton';
import VerticalExpandButton from 'modules/components/VerticalExpandButton';
import BasicTextInput from 'modules/components/Input';
import BasicTextarea from 'modules/components/Textarea';
import BasicSelect from 'modules/components/Select';
import BasicCheckboxGroup from './CheckboxGroup';

export const ExpandButton = styled(BasicExpandButton)`
  position: absolute;
  right: 0;
  top: 0;
  border-top: none;
  border-bottom: none;
  border-right: none;
  z-index: 2;
`;

export const Filters = styled.div`
  padding: 20px 20px 0 20px;
  overflow: auto;
`;

export const FiltersHeader = styled(Panel.Header)`
  display: flex;
  justify-content: flex-start;

  align-items: center;
  flex-shrink: 0;
`;

export const Field = styled.div`
  padding: 10px 0;

  &:first-child {
    padding-top: 0;
  }
`;

export const VerticalButton = styled(VerticalExpandButton)`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: 0 3px 0 0;
`;

const widthStyle = css`
  width: 280px;
`;

export const Select = styled(BasicSelect)`
  ${widthStyle};
`;

export const Textarea = styled(BasicTextarea)`
  ${widthStyle};
`;

export const TextInput = styled(BasicTextInput)`
  ${widthStyle};
`;

export const CheckboxGroup = styled(BasicCheckboxGroup)`
  ${widthStyle};
`;

export const ResetButtonContainer = themed(styled(Panel.Footer)`
  display: flex;
  justify-content: center;
  height: 56px;
  width: 320px;
  box-shadow: ${themeStyle({
    dark: '0px -2px 4px 0px rgba(0,0,0,0.1)',
    light: '0px -1px 2px 0px rgba(0,0,0,0.1)'
  })};
  border-radius: 0;
`);
