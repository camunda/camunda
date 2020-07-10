/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import Collapse from '../Collapse';
import InstancesBar from 'modules/components/InstancesBar';
import PanelListItem from '../PanelListItem';
import {instancesByWorkflow} from 'modules/stores/instancesByWorkflow';

import * as Styled from './styled';
import {
  concatUrl,
  concatTitle,
  concatGroupTitle,
  concatLabel,
  concatGroupLabel,
  concatButtonTitle,
} from './service';
import {INSTANCES_BY_WORKFLOW} from '../constants';
import {Skeleton} from '../Skeleton';
import {observer} from 'mobx-react';

const InstancesByWorkflow = observer(
  class InstancesByWorkflow extends React.Component {
    componentDidMount = async () => {
      instancesByWorkflow.getInstancesByWorkflow();
    };

    componentWillUnmount = async () => {
      instancesByWorkflow.reset();
    };

    renderIncidentsPerVersion = (workflowName, items) => {
      return (
        <Styled.VersionList>
          {items.map((item) => {
            const name = item.name || item.bpmnProcessId;
            const totalInstancesCount =
              item.instancesWithActiveIncidentsCount +
              item.activeInstancesCount;
            return (
              <Styled.VersionLi key={item.workflowId}>
                <PanelListItem
                  to={concatUrl({
                    bpmnProcessId: item.bpmnProcessId,
                    versions: [item],
                    hasFinishedInstances: totalInstancesCount === 0,
                    name,
                  })}
                  title={concatTitle(
                    item.name || workflowName,
                    totalInstancesCount,
                    item.version
                  )}
                  boxSize="small"
                >
                  <InstancesBar
                    label={concatLabel(
                      item.name || workflowName,
                      totalInstancesCount,
                      item.version
                    )}
                    incidentsCount={item.instancesWithActiveIncidentsCount}
                    activeCount={item.activeInstancesCount}
                    size="small"
                    barHeight={3}
                  />
                </PanelListItem>
              </Styled.VersionLi>
            );
          })}
        </Styled.VersionList>
      );
    };

    renderIncidentByWorkflow = (item) => {
      const name = item.workflowName || item.bpmnProcessId;
      const totalInstancesCount =
        item.instancesWithActiveIncidentsCount + item.activeInstancesCount;

      return (
        <PanelListItem
          to={concatUrl({
            bpmnProcessId: item.bpmnProcessId,
            versions: item.workflows,
            hasFinishedInstances: totalInstancesCount === 0,
            name,
          })}
          title={concatGroupTitle(
            name,
            totalInstancesCount,
            item.workflows.length
          )}
        >
          <InstancesBar
            label={concatGroupLabel(
              name,
              totalInstancesCount,
              item.workflows.length
            )}
            incidentsCount={item.instancesWithActiveIncidentsCount}
            activeCount={item.activeInstancesCount}
            size="medium"
            barHeight={5}
          />
        </PanelListItem>
      );
    };

    render() {
      const {
        state: {instances, isFailed, isLoaded},
        isDataAvailable,
      } = instancesByWorkflow;

      if (!isDataAvailable) {
        return (
          <Skeleton
            data={instances}
            isFailed={isFailed}
            isLoaded={isLoaded}
            errorType={INSTANCES_BY_WORKFLOW}
          />
        );
      } else
        return (
          <ul data-test="instances-by-workflow">
            {instances.map((item, index) => {
              const workflowsCount = item.workflows.length;
              const name = item.workflowName || item.bpmnProcessId;
              const IncidentByWorkflowComponent = this.renderIncidentByWorkflow(
                item
              );
              const totalInstancesCount =
                item.instancesWithActiveIncidentsCount +
                item.activeInstancesCount;

              return (
                <Styled.Li
                  key={item.bpmnProcessId}
                  data-test={`incident-byWorkflow-${index}`}
                >
                  {workflowsCount === 1 ? (
                    IncidentByWorkflowComponent
                  ) : (
                    <Collapse
                      content={this.renderIncidentsPerVersion(
                        name,
                        item.workflows
                      )}
                      header={IncidentByWorkflowComponent}
                      buttonTitle={concatButtonTitle(name, totalInstancesCount)}
                    />
                  )}
                </Styled.Li>
              );
            })}
          </ul>
        );
    }
  }
);
export {InstancesByWorkflow};
