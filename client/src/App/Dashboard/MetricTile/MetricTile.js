import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function MetricTile({name, metric, metricColor}) {
  return (
    <div>
      <Styled.Metric metricColor={metricColor || 'themed'}>
        {metric}
      </Styled.Metric>
      <Styled.Name>{name}</Styled.Name>
    </div>
  );
}

MetricTile.propTypes = {
  name: PropTypes.string.isRequired,
  metric: PropTypes.number.isRequired,
  metricColor: PropTypes.oneOf(['allIsWell', 'incidentsAndErrors'])
};
