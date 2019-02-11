import React from 'react';
import PropTypes from 'prop-types';

import TimeStampLabel from '../TimeStampLabel';

import * as Styled from './styled';

const BarComponent = ({node, isSelected, hasBoldTitle, onTreeRowSelection}) => (
  <Styled.Bar isSelected={isSelected} onClick={() => onTreeRowSelection(node)}>
    <Styled.NodeIcon type={node.type} data-test={`flowNodeIcon-${node.type}`} />
    <Styled.NodeName bold={hasBoldTitle}>{node.name}</Styled.NodeName>
    <TimeStampLabel timeStamp={node.endDate} />
  </Styled.Bar>
);

export default React.memo(BarComponent);
export const NoWrapBar = BarComponent;

BarComponent.propTypes = {
  node: PropTypes.shape({
    id: PropTypes.string.isRequired,
    name: PropTypes.string.isRequired,
    type: PropTypes.string.isRequired,
    endDate: PropTypes.string
  }),
  onTreeRowSelection: PropTypes.func,
  isSelected: PropTypes.bool,
  hasBoldTitle: PropTypes.bool
};
