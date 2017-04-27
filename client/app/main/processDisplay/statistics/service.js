import {createLoadCorrelationAction, createLoadCorrelationResultAction, createResetCorrelationAction, createSetHeightAction} from './reducer';
import {dispatchAction} from 'view-utils';
import {post} from 'http';
import {getFilterQuery} from 'utils';
import {addNotification} from 'notifications';
import {getDefinitionId} from '../service';
import {getFilter} from 'main/processDisplay/controls/filter';

export function resetStatisticData() {
  dispatchAction(createResetCorrelationAction());
}

export function setHeight(height) {
  dispatchAction(createSetHeightAction(height));
}

export function loadStatisticData({endEvent, gateway}) {
  const filter = getFilter();
  const query = {
    end: endEvent,
    gateway,
    processDefinitionId: getDefinitionId(),
    filter: getFilterQuery(filter)
  };

  dispatchAction(createLoadCorrelationAction());
  post('/api/process-definition/correlation', query)
    .then(response => response.json())
    .then(result => {
      result.followingNodes[Object.keys(result.followingNodes)[0]].activitiesReached = 30;
      result.followingNodes[Object.keys(result.followingNodes)[0]].activityCount = 76;
      result.followingNodes[Object.keys(result.followingNodes)[1]].activitiesReached = 4;
      result.followingNodes[Object.keys(result.followingNodes)[1]].activityCount = 40;
      dispatchAction(createLoadCorrelationResultAction(result));
    })
    .catch(err => {
      addNotification({
        status: 'Could not load statistics data',
        isError: true
      });
      dispatchAction(createLoadCorrelationResultAction(null));
    });
}

export function findSequenceFlowBetweenGatewayAndActivity(elementRegistry, gateway, activity) {
  const outgoingFlows = elementRegistry.get(gateway).outgoing;
  const incomingFlows = elementRegistry.get(activity).incoming;
  const matchingFlows = [];

  outgoingFlows.forEach(function(outgoingFlow) {
    incomingFlows.forEach(function(incomingFlow) {
      if (outgoingFlow === incomingFlow) {
        matchingFlows.push(incomingFlow);
      }
    });
  });
  return matchingFlows[0];
}
