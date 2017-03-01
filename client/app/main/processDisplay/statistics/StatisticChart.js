import {jsx, Children} from 'view-utils';
import {LoadingIndicator, Chart} from 'widgets';

export const StatisticChart = ({children, isLoading, data, chartConfig}) => {
  return <div className="chart-container">
    <LoadingIndicator predicate={isLoading}>
      <div className="chart-header">
        <Children children={children} />
      </div>
      <div className="chart">
        <Chart selector={data} config={chartConfig} />
      </div>
    </LoadingIndicator>
  </div>;
};
