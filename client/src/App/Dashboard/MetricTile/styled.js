import styled from 'styled-components';
import {Link} from 'react-router-dom';
import {Colors, themed, themeStyle} from 'modules/theme';
import withStrippedProps from 'modules/utils/withStrippedProps';

const METRIC_COLOR = {
  active: 'allIsWell',
  incidents: 'incidentsAndErrors'
};

export const MetricTile = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
`;

export const Metric = themed(styled(withStrippedProps(['type'])(Link))`
  display: inline-block;
  padding-top: 6px;
  padding-bottom: 16px;
  font-size: 56px;
  opacity: ${({metricColor}) =>
    metricColor === 'themed' &&
    themeStyle({
      dark: 0.9,
      light: 1
    })};
  color: ${({type}) =>
    type === 'running'
      ? themeStyle({dark: '#ffffff', light: Colors.uiLight06})
      : Colors[METRIC_COLOR[type]]};

  &:hover {
    text-decoration: underline;
  }
`);

export const Name = themed(styled.div`
  padding-bottom: 22px;
  font-size: 40px;
  line-height: 1.4;
  opacity: ${themeStyle({
    dark: 0.9,
    light: 1
  })};
  color: ${themeStyle({dark: '#ffffff', light: Colors.uiLight06})};
`);
