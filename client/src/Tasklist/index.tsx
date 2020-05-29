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
import {Panel} from './Panel';
import {Filters} from './Filters';
import {Tasks} from './Tasks';
import {Details} from './Details';
import {Variables} from './Variables';
import {Container, SingleTaskContainer} from './styled';

const Tasklist: React.FC = () => {
  return (
    <>
      <Header />
      <Container>
        <Panel>
          <Filters />
          <Tasks />
        </Panel>
        <Route path={Pages.TaskDetails()}>
          <SingleTaskContainer>
            <Details />
            <Variables />
          </SingleTaskContainer>
        </Route>
      </Container>
    </>
  );
};

export {Tasklist};
