import {jsx, Children, Match, Case, Scope} from 'view-utils';
import {LoadingIndicator, Chart} from 'widgets';

export const StatisticChart = ({children, isLoading, data, chartConfig}) => {
  return <div className="chart-container">
    <LoadingIndicator predicate={isLoading}>
      <div className="chart-header">
        <Children children={children} />
      </div>
      <div className="chart">
        <Match>
          <Case predicate={hasNoData}>
            <div className="no-data-indicator">
              <div>No Data</div>
            </div>
          </Case>
        </Match>
        <Scope selector={data}>
          <Scope selector={data => {return {data};}}>
            <Chart
              absoluteScale={chartConfig.absoluteScale}
              onHoverChange={chartConfig.onHoverChange}
            />
          </Scope>
        </Scope>
      </div>
    </LoadingIndicator>
  </div>;

  function hasNoData(state) {
    return data(state).filter(({value}) => value > 0).length === 0;
  }
};
