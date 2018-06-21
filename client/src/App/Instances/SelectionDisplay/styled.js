import styled from 'styled-components';
import {themed, themeStyle, Colors} from 'modules/theme';

export const DebugView = themed(styled.pre`
  color: ${themeStyle({dark: '#ffffff', light: Colors.uiLight06})};
`);
