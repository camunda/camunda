/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Tab, TabList, Tabs} from '@carbon/react';
import {Link, Route, Switch, useLocation} from 'react-router-dom';

import {ErrorPage} from 'components';
import {t} from 'translation';

import {BranchAnalysis} from './BranchAnalysis';
import {TaskAnalysis} from './TaskAnalysis';

import './Analysis.scss';

export default function Analysis() {
  const {pathname} = useLocation();

  return (
    <div className="Analysis">
      <Tabs selectedIndex={pathname.includes('analysis/branchAnalysis') ? 1 : 0}>
        <TabList aria-label="tabs" className="tabList">
          <Tab as={Link} to="/analysis/taskAnalysis">
            {t('analysis.task.label')}
          </Tab>
          <Tab as={Link} to="/analysis/branchAnalysis">
            {t('analysis.branchAnalysis')}
          </Tab>
        </TabList>
      </Tabs>
      <Switch>
        <Route path="/analysis/" exact component={TaskAnalysis} />
        <Route path="/analysis/branchAnalysis" component={BranchAnalysis} />
        <Route path="/analysis/taskAnalysis" component={TaskAnalysis} />
        <Route path="*" component={() => <ErrorPage noLink />} />
      </Switch>
    </div>
  );
}
