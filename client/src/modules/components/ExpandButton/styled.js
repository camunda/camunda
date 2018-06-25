import styled, {css} from 'styled-components';

import {EXPAND_CONTAINER} from 'modules/utils';
import {themed, themeStyle, Colors} from 'modules/theme';
import {UpBar, DownBar, LeftBar, RightBar} from 'modules/components/Icon';

const bottomRightStyle = css`
  position: absolute;
  right: 0;
  bottom: 0;

  border-bottom: none;
`;

const topLeftStyle = css`
  position: absolute;
  left: 0;
  top: 0;

  border-top: none;
  border-left: none;
  border-bottom: none;
`;

const topRightStyle = css`
  position: absolute;
  right: 0;
  top: 0;

  border-top: none;
  border-right: none;
  border-bottom: none;
`;

const {TOP, BOTTOM, LEFT, RIGHT} = EXPAND_CONTAINER;
const stylesMap = {
  [TOP]: bottomRightStyle,
  [BOTTOM]: topRightStyle,
  [LEFT]: topRightStyle,
  [RIGHT]: topLeftStyle
};

export const ExpandButton = themed(styled.a`
  cursor: pointer;

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
    light: Colors.uiDark06
  })};

  &:hover {
    opacity: ${themeStyle({
      dark: 0.7,
      light: 1
    })};
  }

  &:active {
    opacity: ${themeStyle({
      dark: 1,
      light: 1
    })};
    color: ${themeStyle({
      dark: 0.5,
      light: Colors.uiDark04
    })};
  }
`;

export const Up = themed(styled(UpBar)`
  ${iconStyle};
`);

export const Down = themed(styled(DownBar)`
  ${iconStyle};
`);

export const Left = themed(styled(LeftBar)`
  ${iconStyle};
`);

export const Right = themed(styled(RightBar)`
  ${iconStyle};
`);
