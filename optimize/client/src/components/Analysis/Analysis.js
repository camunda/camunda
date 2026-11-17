/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Route, Switch} from 'react-router-dom';

import {ErrorPage} from 'components';

import {BranchAnalysis} from './BranchAnalysis';
import {TaskAnalysis} from './TaskAnalysis';

import './Analysis.scss';

export default function Analysis() {
  return (
    <div className="Analysis">
      <Switch>
        <Route path="/analysis/branchAnalysis" component={BranchAnalysis} />
        <Route path="/analysis/taskAnalysis" component={TaskAnalysis} />
        <Route path="/analysis/" exact component={TaskAnalysis} />
        <Route path="*" component={() => <ErrorPage noLink />} />
      </Switch>
    </div>
  );
}
