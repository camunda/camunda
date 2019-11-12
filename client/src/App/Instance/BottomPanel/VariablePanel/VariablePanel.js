/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import Variables from '../Variables';
import EmptyPanel from 'modules/components/EmptyPanel';
import {
  FAILED_PLACEHOLDER,
  MULTI_SCOPE_PLACEHOLDER,
  EMPTY_PLACEHOLDER
} from './constants';
import {withData} from 'modules/DataManager';
import Skeleton from './Skeleton';
import SpinnerSkeleton from 'modules/components/Skeletons';
import {LOADING_STATE} from 'modules/constants';

import * as Styled from './styled';

class VariablePanel extends React.Component {
  static propTypes = {
    dataManager: PropTypes.object,
    isRunning: PropTypes.bool,
    variables: PropTypes.array,
    editMode: PropTypes.string.isRequired,
    isEditable: PropTypes.bool.isRequired,
    onVariableUpdate: PropTypes.func.isRequired,
    setEditMode: PropTypes.func.isRequired
  };

  constructor(props) {
    super(props);
    this.state = {
      loadingState: LOADING_STATE.LOADING,
      initialLoad: false
    };

    this.subscriptions = {
      LOAD_VARIABLES: ({state}) => {
        this.setState({loadingState: state, initialLoad: true});
      }
    };
  }

  componentDidMount() {
    this.props.dataManager.subscribe(this.subscriptions);
  }

  componentWillUnmount() {
    this.props.dataManager.unsubscribe(this.subscriptions);
  }

  constructTableReplacement() {
    const {loadingState} = this.state;
    const {variables} = this.props;
    const {LOAD_FAILED, LOADED} = LOADING_STATE;
    let label, type;

    if (loadingState === LOAD_FAILED) {
      type = 'warning';
      label = FAILED_PLACEHOLDER;
    } else if (loadingState === LOADED && !variables) {
      type = 'info';
      label = MULTI_SCOPE_PLACEHOLDER;
    } else {
      return;
    }

    return () => <EmptyPanel type={type} label={label} />;
  }

  constructPlaceHolder() {
    const {initialLoad, loadingState} = this.state;
    const {variables} = this.props;
    const {LOADING, LOADED} = LOADING_STATE;
    let label, type, placeholder, rowHeight;

    if (loadingState === LOADING && !initialLoad) {
      type = 'skeleton';
      rowHeight = 32;
      placeholder = Skeleton;
    } else if (
      loadingState === LOADED &&
      initialLoad &&
      variables &&
      variables.length === 0
    ) {
      type = 'info';
      label = EMPTY_PLACEHOLDER;
    } else {
      return;
    }

    return () => (
      <EmptyPanel
        type={type}
        label={label}
        Skeleton={placeholder}
        rowHeight={rowHeight}
      />
    );
  }

  constructOverlay() {
    const {initialLoad, loadingState} = this.state;
    const {LOADING} = LOADING_STATE;

    if (loadingState === LOADING && initialLoad) {
      return () => (
        <Styled.EmptyPanel type={'skeleton'} Skeleton={SpinnerSkeleton} />
      );
    } else {
      return;
    }
  }

  render() {
    const {
      isRunning,
      isEditable,
      variables,
      editMode,
      onVariableUpdate,
      setEditMode
    } = this.props;

    const TableReplacement = this.constructTableReplacement();

    return (
      <Styled.Variables>
        {!!TableReplacement ? (
          <TableReplacement />
        ) : (
          <Variables
            isRunning={isRunning}
            variables={variables}
            editMode={editMode}
            isEditable={isEditable}
            isLoading={this.state.loadingState === LOADING_STATE.LOADING}
            onVariableUpdate={onVariableUpdate}
            setEditMode={setEditMode}
            Placeholder={this.constructPlaceHolder()}
            Overlay={this.constructOverlay()}
          />
        )}
      </Styled.Variables>
    );
  }
}

export default withData(VariablePanel);
