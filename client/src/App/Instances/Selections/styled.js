import styled from 'styled-components';

import {Colors} from 'modules/theme';
import Panel from 'modules/components/Panel';
import BadgeComponent from 'modules/components/Badge';
import ComboBadgeComponent from 'modules/components/ComboBadge';
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
  border-radius: 3px 0px 0 0;
`;

export const SelectionHeader = styled(Panel.Header)`
  display: flex;
  justify-content: flex-start;

  padding-left: 45px;
  display: flex;
  align-items: center;
  flex-shrink: 0;
  border-radius: 3px 0 0 0;
`;

export const Badge = styled(BadgeComponent)`
  margin-left: 13px;
`;

export const SelectionBadgeLeft = styled(ComboBadgeComponent.Left)`
  background: ${Colors.selections};
  color: #ffffff;
`;

export const SelectionBadgeRight = styled(ComboBadgeComponent.Right)`
  background: rgba(77, 144, 255, 0.75);
  color: #ffffff;
`;

export const SelectionsBadge = styled(BadgeComponent)`
  background-color: ${Colors.selections};
  color: #ffffff;
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
