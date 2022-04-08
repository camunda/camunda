/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {SubNav, ErrorPage} from 'components';
import {t} from 'translation';
import {Route, Switch} from 'react-router-dom';
import {BranchAnalysis} from './BranchAnalysis';
import {OutlierAnalysis} from './OutlierAnalysis';

import './Analysis.scss';

export default function Analysis() {
  return (
    <div className="Analysis">
      <SubNav>
        <SubNav.Item
          name={t('analysis.outlier.label')}
          linksTo="/analysis/outlierAnalysis"
          active={['/analysis', '/analysis/outlierAnalysis']}
        />
        <SubNav.Item
          name={t('analysis.branchAnalysis')}
          linksTo="/analysis/branchAnalysis"
          active={['/analysis/branchAnalysis']}
        />
      </SubNav>
      <Switch>
        <Route path="/analysis/" exact component={OutlierAnalysis} />
        <Route path="/analysis/branchAnalysis" component={BranchAnalysis} />
        <Route path="/analysis/outlierAnalysis" component={OutlierAnalysis} />
        <Route path="*" component={() => <ErrorPage noLink />} />
      </Switch>
    </div>
  );
}
