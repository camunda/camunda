import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

const typeStyle = {
  left: 'none none none solid',
  right: 'none solid none none',
  up: 'solid none none none',
  down: 'none none solid none'
};
export const FoldButton = themed(styled.div`
  padding-left: 8px;

  border-style: ${({type}) => typeStyle[type]};
  border-width: 1px;
  border-color: rgba(
    ${themeStyle({dark: '255, 255, 255, 0.15', light: '28, 31, 35, 0.15'})}
  );

  &:first-child {
  ${themeStyle({
    dark: 'color:rgba(255, 255, 255, 0.5)',
    light: `color:${Colors.uiLight06}`
  })};
`);
