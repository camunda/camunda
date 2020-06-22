/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import {ReactComponent as Retry} from 'modules/components/Icon/retry.svg';
import {ReactComponent as Stop} from 'modules/components/Icon/stop.svg';

export const iconStyle = css`
  opacity: 0.7;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark02,
  })};
  cursor: pointer;
`;

export const RetryIcon = themed(styled(Retry)`
  ${iconStyle};
`);

export const CancelIcon = themed(styled(Stop)`
  ${iconStyle};
`);

const ColorMap = new Map(
  Object.entries({
    default: {
      background: {
        dark: Colors.uiDark04,
        light: Colors.lightButton04,
      },
      border: {
        dark: Colors.uiDark06,
        light: Colors.uiLight03,
      },
    },
    hover: {
      background: {
        dark: Colors.darkButton05,
        light: Colors.lightButton06,
      },
      border: {
        dark: Colors.darkButton02,
        light: Colors.lightButton02,
      },
    },
    active: {
      background: {
        dark: Colors.darkButton03,
        light: Colors.uiLight03,
      },
      border: {
        dark: Colors.uiDark05,
        light: Colors.lightButton03,
      },
    },
  })
);

const dynamicBorderStlyes = (backgroundColor, borderColor) => css`
  /* change color around right border*/
  &:after {
    content: '';
    position: absolute;
    top: -1px;
    right: -1px;
    height: 100%;
    width: 2px;
    z-index: 1;
    background: ${themeStyle(backgroundColor)};
    border-top: 1px solid ${themeStyle(borderColor)};
    border-bottom: 1px solid ${themeStyle(borderColor)};
  }

  /* change color of right border */
  & + :before {
    z-index: 2;
    background: ${themeStyle(borderColor)};
  }
`;

const leftBorderStyles = css`
  &:before {
    content: '';
    position: absolute;
    top: 2px;
    left: 0px;
    height: 20px;
    width: 1px;
    background: ${themeStyle(ColorMap.get('default').border)};
  }

  &:hover&:before {
    background: ${themeStyle(ColorMap.get('hover').border)};
  }

  &:active&:before {
    background: ${themeStyle(ColorMap.get('active').border)};
  }
`;

const firstElementStyles = css`
  &:first-child {
    border-radius: 12px 0 0 12px;
    border-right-width: 0;

    &:hover,
    &:active {
      border-right-width: 0;
    }

    &:hover {
      ${dynamicBorderStlyes(
        ColorMap.get('hover').background,
        ColorMap.get('hover').border
      )}
    }
    &:active {
      ${dynamicBorderStlyes(
        ColorMap.get('active').background,
        ColorMap.get('active').border
      )}
    }

    /* Puts focus in same shape as element */
    > button {
      border-radius: inherit;
    }
  }
`;

const lastElementStyles = css`
  &:last-child {
    border-radius: 0 12px 12px 0;
    border-left-width: 0px;

    &:hover,
    &:active {
      border-left-width: 0;
    }

    /* creates custom left border */
    ${leftBorderStyles}

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
    }

    &:hover {
      ${dynamicBorderStlyes(
        ColorMap.get('hover').background,
        ColorMap.get('hover').border
      )};
    }

    &:active {
      ${dynamicBorderStlyes(
        ColorMap.get('active').background,
        ColorMap.get('active').border
      )}
    }

    /* creates custom left border */
    ${leftBorderStyles}

    > button {
      border-radius: inherit;
    }
  }
`;

const singleElementStyles = css`
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

export const Ul = themed(styled.ul`
  display: inline-flex;
  flex-direction: row;
  border-radius: 12px;
  box-shadow: 0 1px 1px 0
    ${themeStyle({dark: 'rgba(0, 0, 0, 0.3)', light: 'rgba(0, 0, 0, 0.1)'})};
`);

export const Li = themed(styled.li`
  position: relative;
  padding: 1px;
  border: 1px solid ${themeStyle(ColorMap.get('default').border)};
  background: ${themeStyle(ColorMap.get('default').background)};

  ${midElementStyles};
  ${firstElementStyles};
  ${lastElementStyles};
  ${singleElementStyles};

  cursor: pointer;

  &:focus-within {
    z-index: 1;

    &:before,
    &:after {
      z-index: -1;
    }

    &:hover {
      &:after {
        z-index: -1;
      }
    }
  }

  &:hover {
    background: ${themeStyle(ColorMap.get('hover').background)};
    border: 1px solid ${themeStyle(ColorMap.get('hover').border)};
  }

  &:active {
    background: ${themeStyle(ColorMap.get('active').background)};
    border: 1px solid ${themeStyle(ColorMap.get('active').border)};
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
