/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {MenuButton, MenuItem} from '@carbon/react';

import {t} from 'translation';
import {getOptimizeProfile} from 'config';

export default function CreateNewButton({
  createCollection,
  createProcessReport,
  createDashboard,
  collection,
  importEntity,
  kind = 'tertiary',
}) {
  const [optimizeProfile, setOptimizeProfile] = useState();

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  return (
    <MenuButton
      size="md"
      kind={kind}
      label={t('home.createBtn.default')}
      className="CreateNewButton"
    >
      {!collection && (
        <MenuItem onClick={createCollection} label={t('home.createBtn.collection')} />
      )}
      <MenuItem onClick={createDashboard} label={t('home.createBtn.dashboard')} />
      {optimizeProfile === 'platform' ? (
        <MenuItem label={t('home.createBtn.report.default')}>
          <MenuItem onClick={createProcessReport} label={t('home.createBtn.report.process')} />
          <Link to="report/new-combined/edit">
            <MenuItem label={t('home.createBtn.report.combined')} />
          </Link>
          <Link to="report/new-decision/edit">
            <MenuItem label={t('home.createBtn.report.decision')} />
          </Link>
        </MenuItem>
      ) : (
        <MenuItem onClick={createProcessReport} label={t('home.createBtn.report.default')} />
      )}
      <MenuItem onClick={importEntity} label={t('common.importReportDashboard')} />
    </MenuButton>
  );
}
