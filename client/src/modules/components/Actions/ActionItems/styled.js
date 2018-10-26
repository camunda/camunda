import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import {ReactComponent as Retry} from 'modules/components/Icon/retry.svg';
import {ReactComponent as Stop} from 'modules/components/Icon/stop.svg';

export const Ul = themed(styled.ul`
  display: inline-flex;
  flex-direction: row;
  padding: 2px;
  margin: 0px;

  list-style-type: none;
  border-radius: 12px;

  background: ${({children}) =>
    // checks if children are components
    children.reduce((sum, next) => sum || !!next, false) &&
    themeStyle({
      dark: Colors.uiDark01,
      light: 'rgba(98, 98, 110, 0.15)'
    })};

  /* Border between single action items */
  & > li {
    &:not(:last-child) {
      border-right: solid 1px
        ${themeStyle({
          dark: 'rgba(250, 250, 250, 0.15)',
          light: 'rgb(98, 98, 110, 0.15)'
        })};
    }
  }

  /* Button border radius for correct focus style shapes*/

  /* only one child exists */
  & > li:first-child:last-child > button {
    border-radius: 12px;
  }

  & > li:first-child > button {
    border-radius: 12px 0 0 12px;
  }
  & > li:last-child > button {
    border-radius: 0 12px 12px 0;
  }
  & > li:not(:first-child):not(:last-child) > button {
    border-radius: 0px;
  }
`);

export const Button = themed(styled.button`
  display: flex;
  align-items: center;
  padding: 3px;

  background: none;
  border: none;
  border-radius: 12px;

  &:focus {
    outline: none;
    box-shadow: ${themeStyle({
      dark: `0 0 0 1px ${Colors.lightFocusInner},0 0 0 4px ${
        Colors.focusOuter
      }`,

      light: `0 0 0 1px ${Colors.darkFocusInner}, 0 0 0 4px ${
        Colors.focusOuter
      }`
    })};
  }
`);

export const iconStyle = css`
  opacity: 0.7;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark02
  })};
  cursor: pointer;
`;

// import and style any Icon accordingly
export const RetryIcon = themed(styled(Retry)`
  ${iconStyle};
`);

export const CancelIcon = themed(styled(Stop)`
  ${iconStyle};
`);
