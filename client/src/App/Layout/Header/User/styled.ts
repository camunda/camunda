/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import DefaultDropdown from 'modules/components/Dropdown';

const Dropdown = styled(DefaultDropdown)`
  > button > div {
    white-space: nowrap;
    text-overflow: ellipsis;
    max-width: 200px;
    display: block;
    overflow: hidden;
  }

  > ul {
    z-index: 6;
  }
`;

const ProfileDropdown = styled.span`
  display: flex;
  align-items: center;
  margin-left: auto;
  flex-direction: row;
  height: 50%;
`;

const SkeletonBlock = styled.div`
  ${({theme}) => {
    const colors = theme.colors.header.user;

    return css`
      height: 12px;
      width: 120px;
      margin-right: 10px;
      background: ${colors.backgroundColor};
    `;
  }}
`;

export {Dropdown, ProfileDropdown, SkeletonBlock};
