import styled from 'styled-components';

import Panel from 'modules/components/Panel';

import BasicExpandButton from 'modules/components/ExpandButton';

export const ExpandButton = styled(BasicExpandButton)`
  position: absolute;
  right: 0;
  top: 0;
  border-top: none;
  border-bottom: none;
  border-right: none;
`;

export const ResetButtonContainer = styled(Panel.Footer)`
  display: flex;
  justify-content: center;
  height: 56px;
`;

export const Filters = styled.div`
  padding: 20px 20px 0 20px;
  overflow-y: scroll;
`;

export const Field = styled.div`
  padding: 10px 0;

  &:first-child {
    padding-top: 0;
  }
`;
