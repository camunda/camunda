import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

export default function MetricTile({name, metric, metricColor}) {
  return (
    <div>
      {metricColor === 'themed' ? (
        <Styled.ThemedMetric>{metric}</Styled.ThemedMetric>
      ) : (
        <Styled.Metric metricColor={metricColor}>{metric}</Styled.Metric>
      )}
      <Styled.Name>{name}</Styled.Name>
    </div>
  );
}

MetricTile.propTypes = {
  name: PropTypes.string.isRequired,
  metric: PropTypes.number.isRequired,
  metricColor: PropTypes.oneOf(['themed', 'allIsWell', 'incidentsAndErrors'])
    .isRequired
};
