/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Popover, Input, Form, Badge} from 'components';

import './VersionPopover.scss';
import {t} from 'translation';

export default function VersionPopover({versions, selected, onChange, disabled}) {
  const specific = usesSpecificVersions(selected);

  let title = t('common.definitionSelection.none');
  if (selected.length === 1 && selected[0] === 'all') {
    title = t('common.definitionSelection.all');
  } else if (selected.length === 1 && selected[0] === 'latest') {
    title = t('common.definitionSelection.latest') + ' : ' + versions[0].version;
  } else if (selected.length) {
    title = selected.join(', ');
  }

  return (
    <Popover className="VersionPopover" title={title} disabled={disabled}>
      <Form compact>
        <div>
          <Input type="radio" checked={selected[0] === 'all'} onChange={() => onChange(['all'])} />{' '}
          {t('common.definitionSelection.all')}
        </div>
        <div>
          <Input
            type="radio"
            checked={selected[0] === 'latest'}
            onChange={() => onChange(['latest'])}
          />{' '}
          {t('common.definitionSelection.version.alwaysLatest')}
        </div>
        <div>
          <Input type="radio" checked={specific} onChange={() => onChange([versions[0].version])} />{' '}
          {t(
            `common.definitionSelection.version.specific.label${
              versions.length === 1 ? '' : '-plural'
            }`
          )}
        </div>
        <div
          className={classnames('specificVersions', {
            disabled: !specific
          })}
        >
          {versions.map(({version, versionTag}) => {
            return (
              <div key={version}>
                <Input
                  type="checkbox"
                  checked={selected.includes(version)}
                  disabled={!specific}
                  onChange={({target}) => {
                    if (target.checked) {
                      onChange(selected.concat([version]).sort((a, b) => b - a));
                    } else {
                      onChange(selected.filter(selected => selected !== version));
                    }
                  }}
                />
                {version}
                {versionTag && <Badge>{versionTag}</Badge>}
              </div>
            );
          })}
        </div>
      </Form>
    </Popover>
  );
}

function usesSpecificVersions(selection) {
  return !(selection.length === 1 && (selection[0] === 'all' || selection[0] === 'latest'));
}
