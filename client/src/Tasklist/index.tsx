/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import * as React from 'react';
import {Route} from 'react-router-dom';

import {Pages} from 'modules/constants/pages';
import {Header} from './Header';
import {Filters} from './Filters';
import {Tasks} from './Tasks';
import {Task} from './Task';
import {
  Container,
  TasksPanel,
  DetailsPanel,
  NoTaskSelectedMessage,
} from './styled';
import {getCurrentCopyrightNoticeText} from 'modules/utils/getCurrentCopyrightNoticeText';
import {tracking} from 'modules/tracking';

const Tasklist: React.FC = () => {
  return (
    <>
      <Header />
      <Container>
        <TasksPanel
          title="Tasks"
          hasTransparentBackground
          onCollapse={() => {
            tracking.track({eventName: 'panel-closed'});
          }}
          onExpand={() => {
            tracking.track({eventName: 'panel-opened'});
          }}
        >
          <Filters />
          <Tasks />
        </TasksPanel>
        <DetailsPanel title="Details" footer={getCurrentCopyrightNoticeText()}>
          <Route exact path={Pages.Initial()}>
            <NoTaskSelectedMessage>
              Select a Task to view the details
            </NoTaskSelectedMessage>
          </Route>
          <Route path={Pages.TaskDetails()}>
            <Task />
          </Route>
        </DetailsPanel>
      </Container>
    </>
  );
};

export {Tasklist};
