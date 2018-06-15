import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

export const Checkbox = themed(styled.input`
  vertical-align: middle;
  margin: 4px 12px;
  width: 14px;
  height: 14px;
  border-radius: 3px;
`);

export const Label = themed(styled.span`
  opacity: 0.9;
  font-size: 13px;
  color: ${themeStyle({
    dark: '#ececec',
    light: Colors.uiLight06
  })};
`);
