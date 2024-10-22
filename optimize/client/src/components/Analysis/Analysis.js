/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link, Route, Switch, useLocation} from 'react-router-dom';

import {ErrorPage, Tabs} from 'components';
import {t} from 'translation';

import {BranchAnalysis} from './BranchAnalysis';
import {TaskAnalysis} from './TaskAnalysis';

import './Analysis.scss';

export default function Analysis() {
  const {pathname} = useLocation();

  const tabValue = getTabValue(pathname);

  return (
    <div className="Analysis">
      <Switch>
        <Tabs value={tabValue}>
          <Tabs.Tab
            value={0}
            as={Link}
            to="/analysis/taskAnalysis"
            title={t('analysis.task.label')}
          >
            <Route path="/analysis/" exact component={TaskAnalysis} />
            <Route path="/analysis/taskAnalysis" component={TaskAnalysis} />
          </Tabs.Tab>
          <Tabs.Tab as={Link} to="/analysis/branchAnalysis" title={t('analysis.branchAnalysis')}>
            <Route path="/analysis/branchAnalysis" component={BranchAnalysis} />
          </Tabs.Tab>
          <Tabs.Tab hidden>
            <Route path="*" component={() => <ErrorPage noLink />} />
          </Tabs.Tab>
        </Tabs>
      </Switch>
    </div>
  );
}

function getTabValue(pathname) {
  if (!!pathname.match(/\/analysis\/taskAnalysis(\/?)$/) || !!pathname.match(/\/analysis(\/?)$/)) {
    return 0;
  }
  if (pathname.match(/\/analysis\/branchAnalysis(\/?)$/)) {
    return 1;
  }
  return 2;
}
