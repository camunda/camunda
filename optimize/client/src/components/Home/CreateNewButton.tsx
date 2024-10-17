/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps} from 'react';
import {MenuButton, MenuItem} from '@carbon/react';

import {t} from 'translation';

interface CreateNewButtonProps
  extends Pick<ComponentProps<typeof MenuButton>, 'kind' | 'size' | 'tabIndex' | 'disabled'> {
  create: (type: 'report' | 'dashboard' | 'kpi' | 'collection') => void;
  collection?: string;
  importEntity: () => void;
}

export default function CreateNewButton({
  create,
  collection,
  importEntity,
  kind = 'tertiary',
  size = 'md',
  disabled,
  tabIndex,
}: CreateNewButtonProps): JSX.Element {
  return (
    <MenuButton
      size={size}
      kind={kind}
      label={t('home.createBtn.default').toString()}
      className="CreateNewButton"
      disabled={disabled}
      tabIndex={tabIndex}
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
      <MenuItem
        onClick={() => create('report')}
        label={t('home.createBtn.report.default').toString()}
      />
      <MenuItem
        onClick={() => create('kpi')}
        label={t('report.kpiTemplates.processKpi').toString()}
      />
      <MenuItem onClick={importEntity} label={t('common.importReportDashboard').toString()} />
    </MenuButton>
  );
}
