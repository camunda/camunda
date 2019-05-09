/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import withStrippedProps from 'modules/utils/withStrippedProps';
import {ReactComponent as Down} from 'modules/components/Icon/down.svg';
import {ReactComponent as Right} from 'modules/components/Icon/right.svg';
import {themed, ExpandButtonThemes as Themes} from 'modules/theme';

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
`;

export const DownIcon = styled(withStrippedProps(['isSelected'])(Down))`
  ${iconStyle};
`;

export const RightIcon = styled(withStrippedProps(['isSelected'])(Right))`
  ${iconStyle};
`;

export const Icon = themed(styled.div`
  border-radius: 50%;
  border-color: none;
  height: 16px;
  width: 16px;
  z-index: 1;

  svg {
    // default arrow color/opacity
    ${props => Themes[props.expandTheme].default.arrow[props.theme]}};
  }

  &:before {
    border-radius: 50%;
    position: absolute;
    content: '';
    height: 16px;
    width: 16px;
    z-index: -1;

    // default background color/opacity
    ${props => Themes[props.expandTheme].default.background[props.theme]};
  }
`);

export const Button = themed(styled.button`
  display: flex;
  padding: 0;
  background: transparent;

  :hover {
    ${Icon.WrappedComponent}::before {
      // hover background color/opacity
      ${props => Themes[props.expandTheme].hover.background[props.theme]}
    }

    ${Icon.WrappedComponent} {
      svg {
        // hover arrow color/opacity
        ${props => Themes[props.expandTheme].hover.arrow[props.theme]};
      }
    }
  }

  :active {
    ${Icon.WrappedComponent}::before {
      // active background color/opacity
      ${props => Themes[props.expandTheme].active.background[props.theme]}
    }

    ${Icon.WrappedComponent} {
      svg {
        // active arrow color/opacity
        ${props => Themes[props.expandTheme].active.arrow[props.theme]};
      }
    }
  }
`);
