import styled from 'styled-components';

import Panel from 'modules/components/Panel';
import BasicExpandButton from 'modules/components/ExpandButton';
import VerticalExpandButton from 'modules/components/VerticalExpandButton';
import Badge from 'modules/components/Badge';
import {Colors, themed, themeStyle} from 'modules/theme';

export const ExpandButton = styled(BasicExpandButton)`
  position: absolute;
  right: 0;
  top: 0;
  border-top: none;
  border-bottom: none;
  border-right: none;
  z-index: 2;
`;

export const ResetButtonContainer = themed(styled(Panel.Footer)`
  display: flex;
  justify-content: center;
  height: 56px;
  box-shadow: ${themeStyle({
    dark: '0px -2px 4px 0px rgba(0,0,0,0.1)',
    light: '0px -1px 2px 0px rgba(0,0,0,0.1)'
  })};
  border-radius: 0;
`);

export const Filters = styled.div`
  padding: 20px 20px 0 20px;
  overflow: auto;
`;

export const Field = styled.div`
  padding: 10px 0;

  &:first-child {
    padding-top: 0;
  }
`;

export const VerticalButton = styled(VerticalExpandButton)`
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
`;

const backgroundStyle = ({isDefault}) => {
  return !isDefault
    ? ''
    : themeStyle({
        light: Colors.uiLight05,
        dark: Colors.uiDark06
      });
};

export const FiltersBadge = themed(styled(Badge)`
  padding: 0;
  height: 15px;
  width: 15px;
  border-radius: 50%;
  background: ${backgroundStyle};
`);
