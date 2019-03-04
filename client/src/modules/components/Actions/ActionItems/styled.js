/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import {ReactComponent as Retry} from 'modules/components/Icon/retry.svg';
import {ReactComponent as Stop} from 'modules/components/Icon/stop.svg';

const borderColors = {
  default: themeStyle({
    dark: Colors.uiDark06,
    light: Colors.uiLight03
  }),
  hover: themeStyle({
    dark: Colors.darkButton02,
    light: Colors.lightButton02
  }),
  active: themeStyle({
    dark: Colors.uiDark05,
    light: Colors.lightButton03
  })
};

/* creates custom left border */
const borderStyles = css`
  &:before {
    content: '';
    position: absolute;
    top: 2px;
    left: 0px;
    height: 20px;
    width: 1px;

    background: ${borderColors.default};
  }
  &:hover&:before {
    background: ${borderColors.hover};
  }

  &:active&:before {
    background: ${borderColors.active};
  }
`;

const firstElementStyles = css`
  &:first-child&:hover,
  &:first-child&:active {
    border-right-width: 0;
    /* moves the 'before'-border of next siblings to not cause visual glitches */
    & + :before {
      left: -1px;
    }
  }

  /* changes color of siblings 'before'-border */
  &:first-child&:hover {
    & + :before {
      background: ${borderColors.hover};
    }
  }

  &:first-child&:active {
    & + :before {
      background: ${borderColors.active};
    }
  }

  &:first-child {
    border-radius: 12px 0 0 12px;
    border-right-width: 0;

    /* puts focus in same shape as element */
    > button {
      border-radius: inherit;
    }
  }
`;

const lastElementStyles = css`
  /* hide left border */
  &:last-child&:hover,
  &:last-child&:active {
    border-left-width: 0;
  }

  &:last-child {
    border-radius: 0 12px 12px 0;
    border-left-width: 0px;

    /* creates custom left border */
    ${borderStyles};
    /* puts focus in same shape as element */
    > button {
      border-radius: inherit;
    }
  }
`;

const midElementStyles = css`
  &:not(:first-child):not(:last-child) {
    border-radius: 0px;
    border-left-width: 0;
    border-right-width: 0;

    &:hover,
    &:active {
      border-left-width: inherit;
      border-right-width: inherit;
      border-left-width: 0px;
      & + :before {
        left: -1px;
      }
    }

    &:not(:first-child):not(:last-child)&:hover {
      & + :before {
        background: ${borderColors.hover};
      }
    }

    &:not(:first-child):not(:last-child)&:active {
      & + :before {
        background: ${borderColors.active};
      }
    }

    /* creates custom left border */
    ${borderStyles};

    > button {
      border-radius: inherit;
    }
  }
`;

export const Ul = themed(styled.ul`
  display: inline-flex;
  flex-direction: row;
  border-radius: 12px;
`);

const onlyElementStyles = css`
  &:first-child&:last-child {
    border-radius: 12px;
    border-right-width: 1px;
    border-left-width: 1px;

    &:before,
    &:after {
      display: none;
    }
  }
`;

export const Li = themed(styled.li`
  padding: 1px;
  border: 1px solid ${borderColors.default};
  position: relative;
  cursor: pointer;
  background: ${themeStyle({
    dark: Colors.uiDark01,
    light: Colors.lightButton04
  })};

  ${firstElementStyles};
  ${midElementStyles};
  ${lastElementStyles};
  ${onlyElementStyles};

  /* color changes through interaction*/
  &:hover {
    background: ${themeStyle({
      dark: Colors.darkButton05,
      light: Colors.lightButton02
    })};
    border: 1px solid ${borderColors.hover};
  }
  &:active {
    background: ${themeStyle({
      dark: Colors.darkButton03,
      light: Colors.uiLight03
    })};
    border: 1px solid ${borderColors.active};
  }
`);

export const Button = themed(styled.button`
  display: flex;
  align-items: center;
  padding: 3px;

  background: none;
  border: none;
  border-radius: 12px;
`);

export const iconStyle = css`
  opacity: 0.7;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark02
  })};
  cursor: pointer;
`;

export const RetryIcon = themed(styled(Retry)`
  ${iconStyle};
`);

export const CancelIcon = themed(styled(Stop)`
  ${iconStyle};
`);
