import React from 'react';
import PropTypes from 'prop-types';

import {FILTER_SELECTION} from 'modules/constants';
import {getFilterQueryString} from 'modules/utils/filter';

import * as Styled from './styled';

export default function MetricTile({label, type, value}) {
  const query = getFilterQueryString(FILTER_SELECTION[type]);

  return (
    <Styled.MetricTile to={`/instances${query}`} type={type}>
      <Styled.Metric>{value}</Styled.Metric>
      <Styled.Label>{label}</Styled.Label>
    </Styled.MetricTile>
  );
}

MetricTile.propTypes = {
  type: PropTypes.string.isRequired,
  label: PropTypes.string.isRequired,
  value: PropTypes.number.isRequired,
  metricColor: PropTypes.oneOf(['allIsWell', 'incidentsAndErrors'])
};
