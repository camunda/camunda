/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import Skeleton from './Skeleton';
import EmptyPanel from 'modules/components/EmptyPanel';
import {withData} from 'modules/DataManager';
import {LOADING_STATE} from 'modules/constants';
import {FlowNodeInstancesTree} from '../FlowNodeInstancesTree';
import {observer} from 'mobx-react';
import {flowNodeInstance} from 'modules/stores/flowNodeInstance';
import * as Styled from './styled';

const FlowNodeInstanceLog = observer(
  class FlowNodeInstanceLog extends React.Component {
    static propTypes = {
      dataManager: PropTypes.object,
      diagramDefinitions: PropTypes.object,
      getNodeWithMetaData: PropTypes.func,
      onTreeRowSelection: PropTypes.func,
    };

    constructor(props) {
      super(props);

      this.state = {
        loadingStateDefinitions: LOADING_STATE.LOADING,
      };
      this.subscriptions = {
        LOAD_STATE_DEFINITIONS: ({state}) => {
          if (
            state === LOADING_STATE.LOADED ||
            state === LOADING_STATE.LOAD_FAILED
          ) {
            this.setState({loadingStateDefinitions: state});
          }
        },
      };
    }

    componentDidMount() {
      this.props.dataManager.subscribe(this.subscriptions);
    }

    componentWillUnmount() {
      this.props.dataManager.unsubscribe(this.subscriptions);
    }

    constructLabel() {
      let label, type;
      const {loadingStateDefinitions} = this.state;

      const {LOADING, LOAD_FAILED} = LOADING_STATE;
      const {
        isFailed: isStateInstanceTreeFailed,
        isInitialLoadComplete: isStateInstanceTreeLoaded,
      } = flowNodeInstance.state;

      if (
        isStateInstanceTreeFailed ||
        loadingStateDefinitions === LOAD_FAILED
      ) {
        type = 'warning';
        label = 'Activity Instances could not be fetched';
      } else if (
        !isStateInstanceTreeLoaded ||
        loadingStateDefinitions === LOADING
      ) {
        type = 'skeleton';
      }
      return {label, type};
    }

    render() {
      const {label, type} = this.constructLabel();
      const {
        instanceExecutionHistory,
        isInstanceExecutionHistoryAvailable,
      } = flowNodeInstance;
      const {
        diagramDefinitions,
        getNodeWithMetaData,
        onTreeRowSelection,
      } = this.props;

      return (
        <Styled.Panel>
          {diagramDefinitions && isInstanceExecutionHistoryAvailable ? (
            <Styled.FlowNodeInstanceLog>
              <Styled.NodeContainer>
                <FlowNodeInstancesTree
                  node={instanceExecutionHistory}
                  getNodeWithMetaData={getNodeWithMetaData}
                  treeDepth={1}
                  onTreeRowSelection={onTreeRowSelection}
                />
              </Styled.NodeContainer>
            </Styled.FlowNodeInstanceLog>
          ) : (
            <Styled.FlowNodeInstanceSkeleton data-test="flownodeInstance-skeleton">
              <EmptyPanel
                label={label}
                type={type}
                Skeleton={Skeleton}
                rowHeight={28}
              />
            </Styled.FlowNodeInstanceSkeleton>
          )}
        </Styled.Panel>
      );
    }
  }
);

const WrappedFlowNodeInstanceLog = withData(FlowNodeInstanceLog);
WrappedFlowNodeInstanceLog.WrappedComponent = FlowNodeInstanceLog;

export default WrappedFlowNodeInstanceLog;
