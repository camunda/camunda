import React from 'react';
import {Chart} from 'widgets';
import {isLoaded} from 'utils';
import {LoadingIndicator} from 'widgets/LoadingIndicator.react';

const jsx = React.createElement;

export const chartWidth = 600;
export const chartHeaderHeight = 50;

export class StatisticChart extends React.Component {
  hasNoData() {
    return this.props.data(this.props).filter(({value}) => value > 0).length === 0;
  }

  render() {
    const {correlation, children, data, chartConfig, height} = this.props;

    if (!correlation) {
      return null;
    }

    return (
      <div className="chart-container">
        <LoadingIndicator loading={!isLoaded(correlation)}>
          <div>
            <div className="chart-header" style={{
              height: chartHeaderHeight + 'px',
              lineHeight: chartHeaderHeight + 'px'
            }}>
              {children}
            </div>
            <div className="chart" style={{
              height: 'calc(100% - ' + chartHeaderHeight + 'px)'
            }}>
              {
                this.hasNoData() && (
                <div className="no-data-indicator">
                  <div>No Data</div>
                </div>
                )
              }
              <Chart
                data={data(this.props)}
                absoluteScale={chartConfig.absoluteScale}
                onHoverChange={chartConfig.onHoverChange}
                height={height - chartHeaderHeight}
                width={chartWidth}
              />
            </div>
          </div>
        </LoadingIndicator>
      </div>
    );
  }
}
