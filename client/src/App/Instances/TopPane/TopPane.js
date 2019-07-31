/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Children, cloneElement} from 'react';
import PropTypes from 'prop-types';

import {EXPAND_STATE} from 'modules/constants';

import SplitPane from 'modules/components/SplitPane';
import * as Styled from './styled.js';

export default function TopPane(props) {
  const messages = {
    NoWorkflow: `There is no Workflow selected.\n To see a diagram, select a Workflow in the Filters panel.`,
    NoVersion: `There is more than one version selected for Workflow "${props.workflowName}".\n To see a diagram, select a single version.`
  };

  const renderMessage = message => {
    return (
      <Styled.EmptyMessageWrapper>
        <Styled.DiagramEmptyMessage message={message} />
      </Styled.EmptyMessageWrapper>
    );
  };

  const conditionalRender = types => {
    const renderOptions = {
      renderNoWorkflowMessage: renderMessage(messages['NoWorkflow']),
      renderNoVersionMessage: renderMessage(messages['NoVersion']),
      renderChildren: Children.map(props.children, child => {
        return cloneElement(child, {
          expandState
        });
      })
    };

    return (
      expandState !== 'COLLAPSED' &&
      types.length === 1 &&
      renderOptions[types[0][0]]
    );
  };

  const {
    paneId,
    handleExpand,
    expandState,
    titles,
    children,
    workflowName,
    ...renderConditions
  } = props;

  const conditionTypes = Object.entries(renderConditions).filter(
    entry => entry[1]
  );

  return (
    <Styled.Pane {...{paneId, handleExpand, expandState, titles}}>
      <Styled.PaneHeader>
        <span data-test="instances-diagram-title">{workflowName}</span>
      </Styled.PaneHeader>
      <SplitPane.Pane.Body>
        {conditionalRender(conditionTypes)}
      </SplitPane.Pane.Body>
    </Styled.Pane>
  );
}

TopPane.propTypes = {
  workflowName: PropTypes.string.isRequired,
  renderNoVersionMessage: PropTypes.bool.isRequired,
  renderNoWorkflowMessage: PropTypes.bool.isRequired,
  renderChildren: PropTypes.bool.isRequired,
  children: PropTypes.node,
  // expandable related props copied from splitPane parent;
  paneId: PropTypes.string,
  handleExpand: PropTypes.func,
  expandState: PropTypes.oneOf(Object.values(EXPAND_STATE)),
  titles: PropTypes.object
};
