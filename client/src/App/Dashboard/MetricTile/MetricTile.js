import React from 'react';
import PropTypes from 'prop-types';

import {FILTER_SELECTION} from 'modules/constants/filter';
import {getFilterQueryString} from 'modules/utils/filter';

import * as Styled from './styled';

export default function MetricTile({name, type, metric, metricColor}) {
  const query = getFilterQueryString(FILTER_SELECTION[type]);
  return (
    <Styled.MetricTile>
      <Styled.Metric
        to={`/instances${query}`}
        metricColor={metricColor || 'themed'}
      >
        {metric}
      </Styled.Metric>
      <Styled.Name>{name}</Styled.Name>
    </Styled.MetricTile>
  );
}

MetricTile.propTypes = {
  type: PropTypes.string.isRequired,
  name: PropTypes.string.isRequired,
  metric: PropTypes.number.isRequired,
  metricColor: PropTypes.oneOf(['allIsWell', 'incidentsAndErrors'])
};
