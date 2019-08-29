/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import Dropdown from 'modules/components/Dropdown';

export const Wrapper = themed(styled.div`
  padding: 0 10px;
`);

export const DropdownOption = styled(Dropdown.Option)`
  padding: 0px;
`;

const disabledStyles = css`
  &:disabled {
    cursor: not-allowed;
    color: rgba(255, 255, 255, 0.6);
    box-shadow: none;
  }
`;

export const SelectionButton = themed(styled.button`
  font-family: IBMPlexSans;
  font-size: 13px;
  font-weight: 600;

  height: 26px;
  padding: 4px 11px 5px 11px;


  /* Color */
  background: ${Colors.selections};
  color: rgba(255, 255, 255, 1);

  border-radius: 13px;
  border-color: ${Colors.primaryButton01};

  & > svg {
    vertical-align: text-bottom;
    margin-right: 8px;
  }

  ${disabledStyles}

  &:focus {
    box-shadow: ${themeStyle({
      dark: `0 0 0 1px ${Colors.focusOuter},0 0 0 4px ${Colors.darkFocusInner}`,
      light: `0 0 0 1px ${Colors.focusOuter}, 0 0 0 4px ${Colors.lightFocusInner}`
    })}
`);

export const dropdownButtonStyles = css`
  font-size: 13px;
  font-weight: 600;
  background-color: ${Colors.selections};
  height: 26px;
  border-radius: 13px;
  border: none;
  padding: 4px 11px 5px 11px;
  color: rgba(255, 255, 255, 1);

  ${disabledStyles};
`;
