import styled from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';
import Panel from 'modules/components/Panel';
import BadgeComponent from 'modules/components/Badge';
import BasicCollapsablePanel from 'modules/components/CollapsablePanel';
import BasicExpandButton from 'modules/components/ExpandButton';
import VerticalExpandButton from 'modules/components/VerticalExpandButton';

export const Selections = styled.div`
  position: absolute;
  top: 0;
  right: 0;
  height: 100%;
  display: flex;
  margin-left: 1px;
  z-index: 2;
`;

export const CollapsablePanel = styled(BasicCollapsablePanel)`
  box-shadow: 0 2px 4px 0 rgba(0, 0, 0, 0.5);
`;

export const SelectionHeader = styled(Panel.Header)`
  display: flex;
  justify-content: flex-start;

  padding-left: 45px;
  display: flex;
  flex-shrink: 0;
`;

export const Badge = styled(BadgeComponent)`
  top: 2px;
  margin-left: 13px;
`;

export const ExpandButton = styled(BasicExpandButton)`
  position: absolute;
  left: 0;
  top: 0;
  border-top: none;
  border-bottom: none;
  border-left: none;
  z-index: 3;
`;

export const VerticalButton = styled(VerticalExpandButton)`
  position: absolute;
  top: 0;
  right: 0;
  width: 100%;
  height: 100%;
`;

const backgroundStyle = ({isDefault}) => {
  return !isDefault
    ? Colors.selections
    : themeStyle({
        light: Colors.uiLight05,
        dark: Colors.uiDark06
      });
};

export const SelectionsBadge = themed(styled.span`
  padding: 5px 4.6px;
  border-radius: 8.5px;
  height: 17px;
  min-width: 17px;
  background: ${backgroundStyle};
  display: flex;
  justify-items: center;
  align-items: center;
  font-size: 12px;
`);
