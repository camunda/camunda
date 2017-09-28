import React from 'react';
import {Chart} from 'widgets';
import {isLoaded} from 'utils';
import {LoadingIndicator} from 'widgets/LoadingIndicator.react';

const jsx = React.createElement;

export const chartWidth = 600;
export const chartHeaderHeight = 50;

export class StatisticChart extends React.Component {
  constructor(props) {
    super(props);

    this.processData(props);
  }

  processData(props) {
    if (props.correlation) {
      this.hasData = !props.correlation || props.data(props).filter(({value}) => value > 0).length > 0;
      this.data = props.data(props);
      this.loading = !isLoaded(props.correlation);
    } else {
      this.hasData = false;
      this.data = [];
      this.loading = true;
    }
  }

  componentWillReceiveProps = this.processData;

  render() {
    const {correlation, children, chartConfig, height} = this.props;

    if (!correlation) {
      return null;
    }

    return (
      <div className="chart-container">
        <LoadingIndicator loading={this.loading}>
          <div>
            <div className="chart-header" style={{
              height: chartHeaderHeight + 'px',
              lineHeight: chartHeaderHeight + 'px'
            }}>
              {children}
            </div>
            <div className="chart" style={{
              height: `calc(100% - ${chartHeaderHeight}px)`
            }}>
              {
                !this.hasData && (
                <div className="no-data-indicator">
                  <div>No Data</div>
                </div>
                )
              }
              <Chart
                data={this.data}
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
