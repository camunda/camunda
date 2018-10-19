import React from 'react';
import PropTypes from 'prop-types';

import {FILTER_SELECTION} from 'modules/constants';
import {getFilterQueryString} from 'modules/utils/filter';
import {withCollapsablePanel} from 'modules/contexts/CollapsablePanelContext';

import * as Styled from './styled';

function MetricTile({label, type, value, expandFilters}) {
  const query = getFilterQueryString(FILTER_SELECTION[type]);

  return (
    <Styled.MetricTile
      to={`/instances${query}`}
      type={type}
      onClick={expandFilters}
    >
      <Styled.Metric>{value}</Styled.Metric>
      <Styled.Label>{label}</Styled.Label>
    </Styled.MetricTile>
  );
}

export default withCollapsablePanel(MetricTile);

MetricTile.propTypes = {
  type: PropTypes.string.isRequired,
  label: PropTypes.string.isRequired,
  value: PropTypes.number.isRequired,
  metricColor: PropTypes.oneOf(['allIsWell', 'incidentsAndErrors']),
  expandFilters: PropTypes.func.isRequired
};
