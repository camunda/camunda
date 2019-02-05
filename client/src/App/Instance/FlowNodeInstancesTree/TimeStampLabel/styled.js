import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const TimeStamp = themed(styled.span`
  margin-left: 14px;
  padding: 2px 4px;

  background: ${themeStyle({
    dark: Colors.darkScopeLabel,
    light: Colors.lightScopeLabel
  })};

  font-size: 11px;
  border-radius: 2px;
`);
