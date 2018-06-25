import styled, {css} from 'styled-components';

import {EXPAND_CONTAINER} from 'modules/utils';
import {themed, themeStyle, Colors} from 'modules/theme';
import {UpBar, DownBar} from 'modules/components/Icon';

const topExpandStyle = css`
  position: absolute;
  right: 0;
  bottom: 0;

  border-bottom: none;
`;

const bottomExpandStyle = css`
  position: absolute;
  right: 0;
  top: 0;

  border-top: none;
  border-right: none;
  border-bottom: none;
`;

const {TOP, BOTTOM} = EXPAND_CONTAINER;
const stylesMap = {
  [TOP]: topExpandStyle,
  [BOTTOM]: bottomExpandStyle
};

export const ExpandButton = themed(styled.a`
  padding-left: 11.5px;
  padding-right: 15px;
  padding-top: 13px;
  padding-bottom: 13px;
  width: 39px;
  height: 38px;

  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};

  ${({containerId}) => stylesMap[containerId]};
`);

const iconStyle = css`
  width: 16px;
  height: 16px;
  object-fit: contain;
  opacity: ${themeStyle({
    dark: 0.5,
    light: 0.9
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
`;

export const Up = themed(styled(UpBar)`
  ${iconStyle};
`);

export const Down = themed(styled(DownBar)`
  ${iconStyle};
`);
