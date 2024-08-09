/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState, ComponentProps} from 'react';
import {Link} from 'react-router-dom';
import {MenuButton, MenuItem} from '@carbon/react';

import {t} from 'translation';
import {getOptimizeProfile} from 'config';

interface CreateNewButtonProps {
  create: (type: 'report' | 'dashboard' | 'kpi' | 'collection') => void;
  collection?: string;
  importEntity: () => void;
  kind?: ComponentProps<typeof MenuButton>['kind'];
  size?: ComponentProps<typeof MenuButton>['size'];
}

export default function CreateNewButton({
  create,
  collection,
  importEntity,
  kind = 'tertiary',
  size = 'md',
}: CreateNewButtonProps): JSX.Element {
  const [optimizeProfile, setOptimizeProfile] = useState<'platform' | 'cloud' | 'ccsm'>();

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  return (
    <MenuButton
      size={size}
      kind={kind}
      label={t('home.createBtn.default').toString()}
      className="CreateNewButton"
    >
      {!collection && (
        <MenuItem
          onClick={() => create('collection')}
          label={t('home.createBtn.collection').toString()}
        />
      )}
      <MenuItem
        onClick={() => create('dashboard')}
        label={t('home.createBtn.dashboard').toString()}
      />
      {optimizeProfile === 'platform' ? (
        <MenuItem label={t('home.createBtn.report.default').toString()}>
          <MenuItem
            onClick={() => create('report')}
            label={t('home.createBtn.report.process').toString()}
          />
          <MenuItem
            onClick={() => create('kpi')}
            label={t('report.kpiTemplates.processKpi').toString()}
          />
          <Link to="report/new-combined/edit">
            <MenuItem label={t('home.createBtn.report.combined').toString()} />
          </Link>
          <Link to="report/new-decision/edit">
            <MenuItem label={t('home.createBtn.report.decision').toString()} />
          </Link>
        </MenuItem>
      ) : (
        <>
          <MenuItem
            onClick={() => create('report')}
            label={t('home.createBtn.report.default').toString()}
          />
          <MenuItem
            onClick={() => create('kpi')}
            label={t('report.kpiTemplates.processKpi').toString()}
          />
        </>
      )}
      <MenuItem onClick={importEntity} label={t('common.importReportDashboard').toString()} />
    </MenuButton>
  );
}
