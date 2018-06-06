import styled from 'styled-components';
import {Colors, themed} from 'theme';

export const Value = styled.div`
  padding-top: 6px;
  padding-bottom: 16px;
  font-size: 56px;
  text-align: center;
  color: ${({valueColor}) => Colors[valueColor]};
`;

export const themedValue = themed(Value.extend`
  opacity: ${({theme}) => (theme === 'dark' ? 0.9 : 1)};
  color: ${({theme}) => (theme === 'dark' ? '#ffffff' : Colors.uiLight06)};
`);

export const Name = themed(styled.div`
  padding-bottom: 22px;
  font-size: 40px;
  line-height: 1.4;
  text-align: center;
  opacity: ${({theme}) => (theme === 'dark' ? 0.9 : 1)};
  color: ${({theme}) => (theme === 'dark' ? '#ffffff' : Colors.uiLight06)};
`);
