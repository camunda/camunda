import React from 'react';

import * as Styled from './styled.js';

export default function MetricTile({name, metric, metricColor}) {
  return (
    <div>
      {metricColor === 'themed' ? (
        <Styled.themedMetric>{metric}</Styled.themedMetric>
      ) : (
        <Styled.Metric metricColor={metricColor}>{metric}</Styled.Metric>
      )}
      <Styled.Name>{name}</Styled.Name>
    </div>
  );
}
