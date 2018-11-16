import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';
import {ReactComponent as Right} from 'modules/components/Icon/right.svg';
import withStrippedProps from 'modules/utils/withStrippedProps';

export const Collapse = styled.div`
  position: relative;
`;

export const Button = themed(styled.button`
  position: absolute;
  top: 6px;
  left: 0;

  width: 16px;
  height: 16px;
  background: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight03
  })};
  border-radius: 50%;
`);

export const Icon = themed(styled(withStrippedProps(['rotated'])(Right))`
  width: 16px;
  height: 16px;
  position: relative;
  left: -7px;
  top: -1px;

  display: flex;
  align-content: center;
  align-items: center;

  fill: ${themeStyle({
    dark: 'rgba(255, 255, 255, 0.9)',
    light: '#FFFFFF'
  })};

  transform: ${({rotated}) => {
    return rotated ? 'none' : 'rotate(90deg)';
  }};
`);
