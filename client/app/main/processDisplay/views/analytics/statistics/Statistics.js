import React from 'react';
import {StatisticChart} from './StatisticChart';
import {leaveGatewayAnalysisMode} from '../';
import {loadStatisticData, resetStatisticData, findSequenceFlowBetweenGatewayAndActivity} from './service';
import {DragHandle} from './DragHandle';
import {isInitial} from 'utils';
import {createViewUtilsComponentFromReact} from 'reactAdapter';

const jsx = React.createElement;

export class StatisticsReact extends React.Component {
  isSelectionComplete = selection => {
    return selection && selection.EndEvent && selection.Gateway;
  }

  onHoverChange = hovered => {
    return (bar, index) => {
      const viewer = this.props.getBpmnViewer();
      const elementRegistry = viewer.get('elementRegistry');
      const canvas = viewer.get('canvas');

      elementRegistry.forEach((element) => {
        canvas.removeMarker(element, 'chart-hover');
      });

      if (hovered) {
        const {correlation: {data:{followingNodes}}, selection} = this.props;
        const gateway = selection.Gateway;
        const activity = Object.keys(followingNodes)[index];

        const sequenceFlow = findSequenceFlowBetweenGatewayAndActivity(elementRegistry, gateway, activity);

        canvas.addMarker(sequenceFlow, 'chart-hover');
      }
    };
  }

  getChartData = valueFct => {
    return ({selection, correlation}) => {
      const gateway = selection && selection.Gateway;

      if (!correlation.data || !gateway) {
        return [];
      }

      const elementRegistry = this.props.getBpmnViewer().get('elementRegistry');

      return Object.keys(correlation.data.followingNodes).map(key => {
        const data = correlation.data.followingNodes[key];

        const sequenceFlow = findSequenceFlowBetweenGatewayAndActivity(elementRegistry, gateway, key);

        return {
          ...valueFct(data),
          key: sequenceFlow.businessObject.name || elementRegistry.get(key).businessObject.name || key
        };
      });
    };
  }

  relativeData = ({activitiesReached, activityCount}) => {
    return {
      value: activitiesReached / activityCount || 0,
      tooltip: (Math.round((activitiesReached / activityCount || 0) * 10000) / 100) + '% (' + activitiesReached + ' / ' + activityCount + ')'
    };
  }

  absoluteData = ({activityCount}) => {
    return {
      value: activityCount || 0,
      tooltip: activityCount || 0
    };
  }

  getRelativeChartData = this.getChartData(this.relativeData);
  getAbsoluteChartData = this.getChartData(this.absoluteData);

  absoluteHeader = ({followingNodes}) => {
    return Object.keys(followingNodes).reduce((prev, key) => {
      return prev + followingNodes[key].activityCount;
    }, 0);
  }

  relativeHeader = ({total}) => {
    return total;
  }

  getHeader = amountFct => {
    return ({correlation, selection}) => {
      const gateway = selection && selection.Gateway;
      const endEvent = selection && selection.EndEvent;

      if (!correlation || !correlation.data || !gateway || !endEvent) {
        return {};
      }

      const elementRegistry = this.props.getBpmnViewer().get('elementRegistry');

      return {
        gateway: elementRegistry.get(gateway).businessObject.name || gateway,
        endEvent: elementRegistry.get(endEvent).businessObject.name || endEvent,
        amount: amountFct(correlation.data)
      };
    };
  }

  getAbsoluteHeader = this.getHeader(this.absoluteHeader);
  getRelativeHeader = this.getHeader(this.relativeHeader);

  render() {
    const open = this.isSelectionComplete(this.props.selection);
    const relativeHeader = this.getRelativeHeader(this.props);
    const absoluteHeader = this.getAbsoluteHeader(this.props);

    return (
      <div
        className={'statisticsContainer' + (open ? ' open' : '')}
        style={{
          height: open ? this.props.height : 0
        }}>
        <DragHandle height={this.props.height} />
        <button type="button" className="close" onClick={leaveGatewayAnalysisMode}>
          <span>×</span>
        </button>
        <StatisticChart
          data={this.getRelativeChartData}
          chartConfig={{absoluteScale: false, onHoverChange: this.onHoverChange}}
          correlation={this.props.correlation}
          selection={this.props.selection}
          height={this.props.height}>
            Gateway:&nbsp;{relativeHeader.gateway}
            &nbsp;/
            EndEvent:&nbsp;{relativeHeader.endEvent}
            &nbsp;- Amount:&nbsp;{relativeHeader.amount}
        </StatisticChart>
        <StatisticChart
          data={this.getAbsoluteChartData}
          chartConfig={{absoluteScale: true, onHoverChange: this.onHoverChange}}
          correlation={this.props.correlation}
          selection={this.props.selection}
          height={this.props.height}>
            Gateway:&nbsp;{absoluteHeader.gateway}
            &nbsp;- Amount:&nbsp;{absoluteHeader.amount}
      </StatisticChart>
      </div>
    );
  }

  componentDidUpdate() {
    const {selection, correlation} = this.props;

    if (!this.isSelectionComplete(selection) && !isInitial(correlation)) {
      resetStatisticData();
    }

    if (this.isSelectionComplete(selection) && isInitial(correlation)) {
      loadStatisticData(selection);
    }
  }
}

export const Statistics = createViewUtilsComponentFromReact('div', StatisticsReact);
