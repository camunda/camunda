/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {interactions} from 'modules/theme';

const Checkbox = styled.div`
  position: relative;
`;

type LabelProps = {
  checked?: boolean;
};

const Label = styled.label<LabelProps>`
  ${({theme, checked}) => {
    const colors = theme.colors.modules.checkbox;
    const opacity = theme.opacity.modules.checkbox;

    return css`
      display: inline-block;
      margin: 4px 0;
      font-size: 13px;
      line-height: 1.08;
      opacity: ${checked ? opacity.checked : opacity.default};
      color: ${colors.label.color};
    `;
  }}
`;

const Input = styled.input`
  position: absolute;
  z-index: 1;
  opacity: 0;
  height: 100%;
  width: 15px;
  top: 0;
  left: 0;
  margin: 0;
  padding: 0;
`;

type CustomCheckboxProps = {
  checked?: boolean;
  checkboxType?: 'selection';
  focused?: boolean;
  indeterminate?: boolean;
};

const CustomCheckbox = styled.div<CustomCheckboxProps>`
  ${({theme, checked, checkboxType, focused, indeterminate}) => {
    const colors = theme.colors.modules.checkbox.customCheckbox;
    const shadows = theme.shadows.modules.checkbox.customCheckbox;

    return css`
      position: relative;
      display: inline-block;
      margin-right: 12px;
      width: 12px;
      height: 12px;

      &:before {
        content: '';
        position: absolute;
        display: inline-block;
        width: 100%;
        height: 100%;
        border-radius: 3px;
        border: solid 1px ${colors.before.borderColor};
        background-color: ${checked && checkboxType === 'selection'
          ? colors.before.selection.backgroundColor
          : colors.before.backgroundColor};
        ${checked
          ? css`
              box-shadow: ${shadows.before};
            `
          : ''};
        ${checked && checkboxType === 'selection'
          ? css`
              box-shadow: ${shadows.selection};
            `
          : ''};
        ${focused ? interactions.focus.css : ''};
      }

      &:after {
        content: ${checked || indeterminate ? "''" : 'none'};
        position: absolute;
        border-style: solid;
        ${indeterminate
          ? css`
              left: 4px;
              top: 4px;
              width: 6px;
              height: 2px;
              border-width: 0 0 2px 0;
            `
          : css`
              left: 2px;
              top: 4px;
              height: 3px;
              width: 8px;
              border-width: 0 0 2px 2px;
              transform: rotate(-50deg);
            `};
        border-color: ${checkboxType === 'selection'
          ? colors.after.selection.borderColor
          : colors.after.borderColor};
      }
    `;
  }}
`;

export {Checkbox, Label, Input, CustomCheckbox};
