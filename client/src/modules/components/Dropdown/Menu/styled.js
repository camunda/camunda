import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import {DROPDOWN_PLACEMENT} from 'modules/constants';

import {PointerBasics, PointerBody, PointerShadow} from '../styled';

export const DropdownMenu = themed(styled.div`
  /* Positioning */
  position: absolute;
  right: ${({placement}) =>
    placement === DROPDOWN_PLACEMENT.TOP ? '-148px' : '-1px'};
  ${({placement}) =>
    placement === DROPDOWN_PLACEMENT.TOP ? 'bottom: 25px;' : 'top:17px'};

  /* Display & Box Model */
  min-width: 186px;
  margin-top: 5px;
  box-shadow: 0 0 2px 0
    ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.6)',
      light: ' rgba(0, 0, 0, 0.2)'
    })};
  border: 1px solid
    ${themeStyle({dark: Colors.uiDark06, light: Colors.uiLight05})};
  border-radius: 3px;

  /* Color */
  background-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight02
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};

  /* Other */
  &:after,
  &:before {
    ${PointerBasics};
  }

  &:after {
    ${PointerBody};
  }
  &:before {
    ${PointerShadow};
  }
`);
