import React from 'react';
import PropTypes from 'prop-types';

import {formatDate} from 'modules/utils/date';
import {withFlowNodeTimeStampContext} from 'modules/contexts/FlowNodeTimeStampContext';

import * as Styled from './styled';

class TimeStampLabel extends React.PureComponent {
  static propTypes = {
    showTimeStamp: PropTypes.bool.isRequired,
    timeStamp: PropTypes.string
  };

  render() {
    const {showTimeStamp, timeStamp} = this.props;
    return showTimeStamp && timeStamp ? (
      <Styled.TimeStamp>{formatDate(timeStamp)}</Styled.TimeStamp>
    ) : null;
  }
}
export default withFlowNodeTimeStampContext(TimeStampLabel);
