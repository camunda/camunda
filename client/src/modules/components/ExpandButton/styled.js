import styled, {css} from 'styled-components';

import {themed, themeStyle, Colors} from 'modules/theme';
import {UpBar, DownBar, LeftBar, RightBar} from 'modules/components/Icon';

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
