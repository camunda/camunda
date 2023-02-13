/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';

import {Dropdown} from 'components';
import {t} from 'translation';
import {getOptimizeProfile} from 'config';

export default function CreateNewButton({
  createCollection,
  createProcessReport,
  createDashboard,
  collection,
  importEntity,
  user,
  primary,
}) {
  const [optimizeProfile, setOptimizeProfile] = useState();

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  return (
    <Dropdown
      main
      primary={primary}
      label={t('home.createBtn.default')}
      className="CreateNewButton"
    >
      {!collection && (
        <Dropdown.Option onClick={createCollection}>
          {t('home.createBtn.collection')}
        </Dropdown.Option>
      )}
      <Dropdown.Option onClick={createDashboard}>{t('home.createBtn.dashboard')}</Dropdown.Option>
      {optimizeProfile === 'platform' ? (
        <Dropdown.Submenu label={t('home.createBtn.report.default')} openToLeft>
          <Dropdown.Option onClick={createProcessReport}>
            {t('home.createBtn.report.process')}
          </Dropdown.Option>
          <Dropdown.Option link="report/new-combined/edit">
            {t('home.createBtn.report.combined')}
          </Dropdown.Option>
          <Dropdown.Option link="report/new-decision/edit">
            {t('home.createBtn.report.decision')}
          </Dropdown.Option>
        </Dropdown.Submenu>
      ) : (
        <Dropdown.Option onClick={createProcessReport}>
          {t('home.createBtn.report.default')}
        </Dropdown.Option>
      )}
      <Dropdown.Option onClick={importEntity}>{t('common.importReportDashboard')}</Dropdown.Option>
    </Dropdown>
  );
}
