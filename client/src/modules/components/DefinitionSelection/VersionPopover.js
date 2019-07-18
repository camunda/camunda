/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import classnames from 'classnames';

import {Popover, Input, Form} from 'components';

import './VersionPopover.scss';

export default function VersionPopover({versions, selected, onChange, disabled}) {
  const specific = usesSpecificVersions(selected);

  let title = 'None';
  if (selected.length === 1 && selected[0] === 'all') {
    title = 'All';
  } else if (selected.length === 1 && selected[0] === 'latest') {
    title = 'Latest : ' + versions[0].version;
  } else if (selected.length) {
    title = selected.join(', ');
  }

  return (
    <Popover className="VersionPopover" title={title} disabled={disabled}>
      <Form compact>
        <div>
          <Input type="radio" checked={selected[0] === 'all'} onChange={() => onChange(['all'])} />{' '}
          All
        </div>
        <div>
          <Input
            type="radio"
            checked={selected[0] === 'latest'}
            onChange={() => onChange(['latest'])}
          />{' '}
          Always display latest
        </div>
        <div>
          <Input type="radio" checked={specific} onChange={() => onChange([versions[0].version])} />{' '}
          Specific version(s)
        </div>
        <div
          className={classnames('specificVersions', {
            disabled: !specific
          })}
        >
          {versions.map(version => {
            return (
              <div key={version.version}>
                <Input
                  type="checkbox"
                  checked={selected.includes(version.version)}
                  disabled={!specific}
                  onChange={({target}) => {
                    if (target.checked) {
                      onChange(selected.concat([version.version]).sort((a, b) => b - a));
                    } else {
                      onChange(selected.filter(selected => selected !== version.version));
                    }
                  }}
                />
                {version.version}
                <span className="tag">{version.versionTag}</span>
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
