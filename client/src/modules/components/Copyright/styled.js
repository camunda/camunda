import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Copyright = themed(styled.div`
  color: ${themeStyle({dark: '#fff', light: Colors.uiLight06})};
  opacity: ${themeStyle({dark: 0.7, light: 0.9})};
  font-size: 12px;
`);
