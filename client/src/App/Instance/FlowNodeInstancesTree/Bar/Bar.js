import React from 'react';
import PropTypes from 'prop-types';

import TimeStampLabel from '../TimeStampLabel';

import * as Styled from './styled';

const BarComponent = ({node, isSelected}) => {
  return (
    <Styled.Bar isSelected={isSelected}>
      <Styled.NodeIcon
        type={node.type}
        data-test={`flowNodeIcon-${node.type}`}
      />
      <Styled.NodeName isBold={!!node.children.length}>
        {node.name}
      </Styled.NodeName>
      <TimeStampLabel timeStamp={node.endDate} isSelected={isSelected} />
    </Styled.Bar>
  );
};

export default React.memo(BarComponent);
export const NoWrapBar = BarComponent;

BarComponent.propTypes = {
  node: PropTypes.shape({
    id: PropTypes.string.isRequired,
    name: PropTypes.string.isRequired,
    type: PropTypes.string.isRequired,
    endDate: PropTypes.string
  }),
  isSelected: PropTypes.bool
};
