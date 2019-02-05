import React from 'react';
import PropTypes from 'prop-types';

import {PILL_TYPE} from 'modules/constants';

import {withFlowNodeTimeStampContext} from 'modules/contexts/FlowNodeTimeStampContext';

import * as Styled from './styled';

class TimeStampPill extends React.Component {
  static propTypes = {
    onTimeStampToggle: PropTypes.func.isRequired,
    showTimeStamp: PropTypes.bool.isRequired
  };

  render() {
    const {showTimeStamp, onTimeStampToggle} = this.props;
    return (
      <Styled.TimeStampPill
        isActive={showTimeStamp}
        onClick={onTimeStampToggle}
        type={PILL_TYPE.TIMESTAMP}
      >
        {`${showTimeStamp ? 'Hide' : 'Show'} Timestamps`}
      </Styled.TimeStampPill>
    );
  }
}
export default withFlowNodeTimeStampContext(TimeStampPill);
