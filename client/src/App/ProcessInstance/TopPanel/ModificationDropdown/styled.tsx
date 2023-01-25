/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as Move} from 'modules/components/Icon/move.svg';
import {ReactComponent as Stop} from 'modules/components/Icon/stop.svg';
import {ReactComponent as Plus} from 'modules/components/Icon/plus.svg';
import {styles} from '@carbon/elements';
import {Popover as DefaultPopover} from 'modules/components/Popover';
import {Spinner as BaseSpinner} from 'modules/components/Spinner';

const iconStyle = css`
  margin-right: 8px;
`;

const MoveIcon = styled(Move)`
  ${iconStyle}
`;

const CancelIcon = styled(Stop)`
  ${iconStyle}
`;

const AddIcon = styled(Plus)`
  ${iconStyle}
`;

const Popover = styled(DefaultPopover)`
  padding-bottom: 4px;
`;

const Title = styled.div`
  ${styles.label01};
  padding: 13px 19px 6px 15px;
`;

const Options = styled.div`
  display: flex;
  flex-direction: column;
`;

const Option = styled.button`
  ${({theme}) => {
    const colors = theme.colors.modules.popover;

    return css`
      display: flex;
      align-items: center;
      background-color: ${colors.backgroundColor};
      color: ${colors.modificationsDropdown.color};
      width: 100%;
      height: 100%;
      padding: 9px 16px 9px 15px;
      ${styles.label02};
      font-weight: 500;

      &:hover {
        color: ${theme.colors.linkHover};
      }

      &:active {
        color: ${theme.colors.linkActive};
      }
    `;
  }}
`;

const Unsupported = styled.div`
  padding: 9px 16px 9px 15px;
  ${styles.label02};
  font-style: italic;
`;

const SelectedInstanceCount = styled.div`
  padding: 2px 16px 1px 15px;
  font-size: 11px;
  font-style: italic;
`;

const Spinner = styled(BaseSpinner)`
  margin: 10px 0 15px 0;
  align-self: center;
  height: 16px;
  width: 16px;
`;

export {
  Popover,
  Title,
  Options,
  Option,
  MoveIcon,
  AddIcon,
  CancelIcon,
  Unsupported,
  SelectedInstanceCount,
  Spinner,
};
