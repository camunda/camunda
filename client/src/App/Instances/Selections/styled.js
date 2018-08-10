import styled from 'styled-components';
import Panel from 'modules/components/Panel';
import BadgeComponent from 'modules/components/Badge';
import ExpandButton from 'modules/components/ExpandButton';

export const Selections = styled.div`
  width: 479px;
  display: flex;
  margin-left: 1px;
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

export const RightExpandButton = styled(ExpandButton)`
  position: absolute;
  left: 0;
  top: 0;
  border-top: none;
  border-bottom: none;
  border-left: none;
`;
