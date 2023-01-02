/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {Popover as BasePopover} from 'modules/components/Popover';
import {zDateRangePopover} from 'modules/constants/componentHierarchy';

const Popover = styled(BasePopover)`
  z-index: ${zDateRangePopover};
`;

const Title = styled.div`
  ${({theme}) => {
    const {titleColor} = theme.colors.dateRangePopover;
    return css`
      font-size: 14px;
      font-weight: 500;
      margin-bottom: 16px;
      color: ${titleColor};
    `;
  }}
`;

const Body = styled.div`
  padding: 16px;

  // Make Carbon input look similar to Operate's current style.
  // Can be removed, once Operate is carbonized (see #3629).
  ${({theme}) => {
    return css`
      label {
        color: ${theme.colors.dateRangePopover.color};
      }

      input {
        border: 1px solid ${theme.colors.modules.input.borderColor};
        border-radius: 3px;

        background: ${theme.colors.modules.input.backgroundColor};
        color: ${theme.colors.modules.input.color};
        &::placeholder {
          color: ${theme.colors.modules.input.placeholder.color};
        }
      }
    `;
  }}

  .cds--text-input-wrapper {
    width: 98px;

    &:not(:first-child) {
      margin-left: 1px;
    }
  }

  .cds--layer-one {
    background: unset;
  }.
`;

const Footer = styled.div`
  ${({theme}) => {
    const {borderColor} = theme.colors.dateRangePopover;
    return css`
      display: flex;
      justify-content: flex-end;
      padding: 16px;
      button:not(:first-child) {
        margin-left: 16px;
      }
      border-top: 1px solid ${borderColor};
    `;
  }}
`;

const DatePickerContainer = styled.div`
  display: flex;
  justify-content: center;

  .flatpickr-calendar {
    width: 280px;
  }
`;

const TimeInputContainer = styled.div`
  display: flex;
  justify-content: center;
  margin-top: 16px;
`;

export {Popover, Title, Footer, Body, DatePickerContainer, TimeInputContainer};
