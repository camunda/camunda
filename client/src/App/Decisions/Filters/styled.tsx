/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {
  CmDropdown,
  CmText,
  CmCheckbox,
  CmIcon,
} from '@camunda-cloud/common-ui-react';
import {TextField as BaseTextField} from 'modules/components/TextField';
import {Panel} from 'modules/components/Panel';

const SectionTitle = styled(CmText)``;

const Checkbox = styled(CmCheckbox)``;

const TextField = styled(BaseTextField)``;

const Fields = styled.div`
  ${({theme}) => {
    return css`
      width: 100%;
      height: calc(100% - 37px);
      padding: 23px 20px 0 20px;
      display: flex;
      flex-direction: column;
      background-color: ${theme.colors.itemEven};
      overflow-y: auto;

      ${SectionTitle}:not(:last-child) {
        padding-bottom: 8px;
      }

      ${Checkbox}:not(:last-child) {
        padding-bottom: 12px;
      }
    `;
  }}
`;

const DeleteIcon = styled(CmIcon)`
  object-fit: contain;
  display: none;
`;

const FormGroup = styled.div`
  width: 100%;
  height: fit-content;
  padding-bottom: 24px;
  display: flex;
  flex-direction: column;
  position: relative;

  &:hover ${DeleteIcon} {
    position: absolute;
    display: block;
    right: 8px;
    top: 0px;
    cursor: pointer;
  }
`;

const FormElement = styled.form`
  width: 100%;
  height: 100%;
  padding-bottom: 1px;
`;

const Dropdown = styled(CmDropdown)`
  display: flex;
  justify-content: flex-end;
  margin: 0 3px 16px 0;
`;

const ResetButtonContainer = styled(Panel.Footer)`
  ${({theme}) => {
    const shadow = theme.shadows.filters.resetButtonContainer;

    return css`
      display: flex;
      justify-content: center;
      width: 100%;
      box-shadow: ${shadow};
      border-radius: 0;
    `;
  }}
`;

const OptionalFilters = styled.div`
  width: 100%;
  display: flex;
  flex-direction: column-reverse;
`;

export {
  FormElement,
  Dropdown,
  SectionTitle,
  Checkbox,
  TextField,
  ResetButtonContainer,
  Fields,
  FormGroup,
  OptionalFilters,
  DeleteIcon,
};
