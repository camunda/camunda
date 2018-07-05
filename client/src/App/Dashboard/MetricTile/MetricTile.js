import React from 'react';
import PropTypes from 'prop-types';

import {FILTER_SELECTION} from 'modules/constants/filter';
import {getFilterQueryString} from 'modules/utils/filter';

import * as Styled from './styled';

export default function MetricTile({label, type, value}) {
  const query = getFilterQueryString(FILTER_SELECTION[type]);

  return (
    <Styled.MetricTile>
      <Styled.Metric to={`/instances${query}`} type={type}>
        {value}
      </Styled.Metric>
      <Styled.Name>{label}</Styled.Name>
    </Styled.MetricTile>
  );
}

MetricTile.propTypes = {
  type: PropTypes.string.isRequired,
  label: PropTypes.string.isRequired,
  value: PropTypes.number.isRequired,
  metricColor: PropTypes.oneOf(['allIsWell', 'incidentsAndErrors'])
};
