/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import DefaultDropdown from 'modules/components/Dropdown';

export const Dropdown = styled(DefaultDropdown)`
  > button > div {
    white-space: nowrap;
    text-overflow: ellipsis;
    max-width: 100px;
    display: block;
    overflow: hidden;
  }

  > ul {
    z-index: 6;
  }
`;

export const ProfileDropdown = styled.span`
  margin-right: 20px;
  display: flex;
  align-items: center;
  margin-left: auto;
  flex-direction: row;
  width: 130px;
  height: 50%;
`;

const colors = css`
  background: ${themeStyle({
    dark: 'rgba(136, 136, 141)',
    light: Colors.uiLight06
  })};
  opacity: ${themeStyle({
    dark: 0.2,
    light: 0.09
  })};
`;

export const SkeletonBlock = themed(styled.div`
  height: 12px;
  width: 120px;
  margin-right: 10px;
  ${colors};
`);
