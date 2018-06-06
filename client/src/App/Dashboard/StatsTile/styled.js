import styled from 'styled-components';
import {Colors, themed} from 'theme';

export const Value = styled.div`
  font-size: 56px;
  text-align: center;
  color: ${props => props.valueColor};
`;

export const themedValue = themed(Value.extend`
  opacity: ${({theme}) => (theme === 'dark' ? 0.9 : 1)};
  color: ${({theme}) => (theme === 'dark' ? '#ffffff' : Colors.uiLight06)};
`);

export const Name = themed(styled.div`
  font-size: 40px;
  line-height: 1.4;
  text-align: center;
  opacity: ${({theme}) => (theme === 'dark' ? 0.9 : 1)};
  color: ${({theme}) => (theme === 'dark' ? '#ffffff' : Colors.uiLight06)};
`);
