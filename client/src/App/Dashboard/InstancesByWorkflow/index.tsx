/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Collapse} from '../Collapse';
import InstancesBar from 'modules/components/InstancesBar';
import PanelListItem from '../PanelListItem';
import {instancesByWorkflowStore} from 'modules/stores/instancesByWorkflow';

import * as Styled from './styled';
import {
  concatUrl,
  concatTitle,
  concatGroupTitle,
  concatLabel,
  concatGroupLabel,
  concatButtonTitle,
} from './service';
import {Skeleton} from '../Skeleton';
import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';

const InstancesByWorkflow = observer(
  class InstancesByWorkflow extends React.Component {
    componentDidMount = async () => {
      instancesByWorkflowStore.getInstancesByWorkflow();
    };

    componentWillUnmount = async () => {
      instancesByWorkflowStore.reset();
    };

    renderIncidentsPerVersion = (workflowName: any, items: any) => {
      return (
        <Styled.VersionList>
          {items.map((item: any) => {
            const name = item.name || item.bpmnProcessId;
            const totalInstancesCount =
              item.instancesWithActiveIncidentsCount +
              item.activeInstancesCount;
            return (
              <Styled.VersionLi key={item.workflowId}>
                <PanelListItem
                  // @ts-expect-error ts-migrate(2322) FIXME: Property 'to' does not exist on type 'IntrinsicAtt... Remove this comment to see the full error message
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
                  $boxSize="small"
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

    renderIncidentByWorkflow = (item: any) => {
      const name = item.workflowName || item.bpmnProcessId;
      const totalInstancesCount =
        item.instancesWithActiveIncidentsCount + item.activeInstancesCount;

      return (
        <PanelListItem
          // @ts-expect-error ts-migrate(2322) FIXME: Property 'to' does not exist on type 'IntrinsicAtt... Remove this comment to see the full error message
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
      const {instances, status} = instancesByWorkflowStore.state;

      if (['initial', 'fetching'].includes(status)) {
        return <Skeleton />;
      }

      if (status === 'fetched' && instances.length === 0) {
        return (
          <StatusMessage variant="default">
            There are no Workflows deployed
          </StatusMessage>
        );
      }

      if (status === 'error') {
        return (
          <StatusMessage variant="error">
            Instances by Workflow could not be fetched
          </StatusMessage>
        );
      }

      return (
        <ul data-testid="instances-by-workflow">
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
                data-testid={`incident-byWorkflow-${index}`}
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
