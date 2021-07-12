/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';

import {Dropdown} from 'components';
import {t} from 'translation';
import {withUser} from 'HOC';
import {isOptimizeCloudEnvironment} from 'config';

export function CreateNewButton({
  createCollection,
  createProcessReport,
  createDashboard,
  collection,
  importEntity,
  user,
}) {
  const [isOptimizeCloud, setIsOptimizeCloud] = useState(true);

  useEffect(() => {
    (async () => {
      setIsOptimizeCloud(await isOptimizeCloudEnvironment());
    })();
  }, []);

  return (
    <Dropdown main primary label={t('home.createBtn.default')} className="CreateNewButton">
      {!collection && (
        <Dropdown.Option onClick={createCollection}>
          {t('home.createBtn.collection')}
        </Dropdown.Option>
      )}
      <Dropdown.Option onClick={createDashboard}>{t('home.createBtn.dashboard')}</Dropdown.Option>
      <Dropdown.Submenu label={t('home.createBtn.report.default')}>
        <Dropdown.Option onClick={createProcessReport}>
          {t('home.createBtn.report.process')}
        </Dropdown.Option>
        <Dropdown.Option link="report/new-combined/edit">
          {t('home.createBtn.report.combined')}
        </Dropdown.Option>
        {!isOptimizeCloud && (
          <Dropdown.Option link="report/new-decision/edit">
            {t('home.createBtn.report.decision')}
          </Dropdown.Option>
        )}
      </Dropdown.Submenu>
      {user?.authorizations.includes('import_export') && (
        <Dropdown.Option onClick={importEntity}>{t('common.importJSON')}</Dropdown.Option>
      )}
    </Dropdown>
  );
}

export default withUser(CreateNewButton);
