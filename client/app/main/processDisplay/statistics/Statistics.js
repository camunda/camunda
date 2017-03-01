import {jsx, withSelector, Class, OnEvent, Scope, Text} from 'view-utils';
import {StatisticChart} from './StatisticChart';
import {leaveGatewayAnalysisMode} from '../diagram/analytics/service';
import {loadStatisticData, resetStatisticData, findSequenceFlowBetweenGatewayAndActivity} from './service';
import {isInitial, isLoading} from 'utils';

export const Statistics = withSelector(({getBpmnViewer}) => {
  const template = <div className="statisticsContainer">
    <Class className="open" selector="display" predicate={isSelectionComplete} />
    <button type="button" className="close">
      <OnEvent event="click" listener={leaveGatewayAnalysisMode} />
      <span>×</span>
    </button>
    <StatisticChart
      isLoading={isLoadingSomething}
      data={getChartData(relativeData)}
      chartConfig={{absoluteScale: false}}
    >
      <Scope selector={getHeader(relativeHeader)}>
        Gateway:&nbsp;<Text property="gateway" />
        &nbsp;/
        EndEvent:&nbsp;<Text property="endEvent" />
        &nbsp;- Amount:&nbsp;
        <Text property="amount" />
      </Scope>
    </StatisticChart>
    <StatisticChart
      isLoading={isLoadingSomething}
      data={getChartData(absoluteData)}
      chartConfig={{absoluteScale: true}}
    >
      <Scope selector={getHeader(absoluteHeader)}>
        Gateway:&nbsp;<Text property="gateway" />
        &nbsp;- Amount:&nbsp;
        <Text property="amount" />
      </Scope>
    </StatisticChart>
  </div>;

  function isLoadingSomething({statistics: {correlation}}) {
    return isLoading(correlation);
  }

  function absoluteHeader({followingNodes}) {
    return Object.keys(followingNodes).reduce((prev, key) => {
      return prev + followingNodes[key].activityCount;
    }, 0);
  }

  function relativeHeader({total}) {
    return total;
  }

  function getHeader(amountFct) {
    return ({display: {selection}, statistics: {correlation}}) => {
      const gateway = selection && selection.gateway;
      const endEvent = selection && selection.endEvent;

      if (!correlation.data || !gateway || !endEvent) {
        return {};
      }

      const elementRegistry = getBpmnViewer().get('elementRegistry');

      return {
        gateway: elementRegistry.get(gateway).businessObject.name || gateway,
        endEvent: elementRegistry.get(endEvent).businessObject.name || endEvent,
        amount: amountFct(correlation.data)
      };
    };
  }

  function isSelectionComplete({selection}) {
    return selection && selection.endEvent && selection.gateway;
  }

  function relativeData({activitiesReached, activityCount}) {
    return {
      value: activitiesReached / activityCount || 0,
      tooltip: (Math.round((activitiesReached / activityCount || 0) * 10000) / 100) + '% (' + activitiesReached + ' / ' + activityCount + ')'
    };
  }

  function absoluteData({activityCount}) {
    return {
      value: activityCount || 0,
      tooltip: activityCount || 0
    };
  }

  function getChartData(valueFct) {
    return ({statistics: {correlation}, display: {selection}}) => {
      const gateway = selection && selection.gateway;

      if (!correlation.data || !gateway) {
        return [];
      }

      const elementRegistry = getBpmnViewer().get('elementRegistry');

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

  return (parentNode, eventsBus) => {
    const templateUpdate = template(parentNode, eventsBus);

    return [templateUpdate, ({display, statistics: {correlation}, controls}) => {
      if (!isSelectionComplete(display) && !isInitial(correlation)) {
        resetStatisticData();
      }
      if (isSelectionComplete(display) && isInitial(correlation)) {
        loadStatisticData(display.selection, controls);
      }
    }];
  };
});
