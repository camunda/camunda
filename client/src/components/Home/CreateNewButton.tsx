/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState, ComponentProps} from 'react';
import {Link} from 'react-router-dom';
import {MenuButton, MenuItem} from '@carbon/react';

import {t} from 'translation';
import {getOptimizeProfile} from 'config';

interface CreateNewButtonProps {
  createCollection: () => void;
  createProcessReport: () => void;
  createDashboard: () => void;
  collection?: string;
  importEntity: () => void;
  kind?: ComponentProps<typeof MenuButton>['kind'];
}

export default function CreateNewButton({
  createCollection,
  createProcessReport,
  createDashboard,
  collection,
  importEntity,
  kind = 'tertiary',
}: CreateNewButtonProps): JSX.Element {
  const [optimizeProfile, setOptimizeProfile] = useState<'platform' | 'cloud' | 'ccsm'>();

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  return (
    <MenuButton
      size="md"
      kind={kind}
      label={t('home.createBtn.default').toString()}
      className="CreateNewButton"
    >
      {!collection && (
        <MenuItem onClick={createCollection} label={t('home.createBtn.collection').toString()} />
      )}
      <MenuItem onClick={createDashboard} label={t('home.createBtn.dashboard').toString()} />
      {optimizeProfile === 'platform' ? (
        <MenuItem label={t('home.createBtn.report.default').toString()}>
          <MenuItem
            onClick={createProcessReport}
            label={t('home.createBtn.report.process').toString()}
          />
          <Link to="report/new-combined/edit">
            <MenuItem label={t('home.createBtn.report.combined').toString()} />
          </Link>
          <Link to="report/new-decision/edit">
            <MenuItem label={t('home.createBtn.report.decision').toString()} />
          </Link>
        </MenuItem>
      ) : (
        <MenuItem
          onClick={createProcessReport}
          label={t('home.createBtn.report.default').toString()}
        />
      )}
      <MenuItem onClick={importEntity} label={t('common.importReportDashboard').toString()} />
    </MenuButton>
  );
}
