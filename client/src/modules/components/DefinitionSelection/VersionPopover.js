/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';

import {Popover, LabeledInput, Form, Badge} from 'components';

import './VersionPopover.scss';
import {t} from 'translation';

export default function VersionPopover({
  versions,
  selected,
  selectedSpecificVersions = [],
  onChange,
  disabled,
  tooltip,
}) {
  const specific = usesSpecificVersions(selected);

  let title = t('common.none');
  if (selected.length === 1 && selected[0] === 'all') {
    title = t('common.all');
  } else if (selected.length === 1 && selected[0] === 'latest') {
    title = t('common.definitionSelection.latest') + ' : ' + versions[0]?.version;
  } else if (selected.length) {
    title = selected.join(', ');
  }

  return (
    <Popover
      className="VersionPopover"
      tooltip={tooltip}
      title={title}
      disabled={disabled || !versions}
    >
      <Form compact>
        <Form.Group>
          <LabeledInput
            label={t('common.all')}
            type="radio"
            checked={selected[0] === 'all'}
            onChange={() => onChange(['all'])}
          />
          <LabeledInput
            label={t('common.definitionSelection.version.alwaysLatest')}
            type="radio"
            checked={selected[0] === 'latest'}
            onChange={() => onChange(['latest'])}
          />
          <LabeledInput
            label={t(
              `common.definitionSelection.version.specific.label${
                versions?.length === 1 ? '' : '-plural'
              }`
            )}
            type="radio"
            checked={specific}
            onChange={() => onChange(selectedSpecificVersions)}
          />
          <Form.Group
            noSpacing
            className={classnames('specificVersions', {
              disabled: !specific,
            })}
          >
            {versions?.map(({version, versionTag}) => {
              return (
                <LabeledInput
                  key={version}
                  label={
                    <>
                      {version}
                      {versionTag && <Badge>{versionTag}</Badge>}
                    </>
                  }
                  type="checkbox"
                  checked={selectedSpecificVersions.includes(version)}
                  disabled={!specific}
                  onChange={({target}) => {
                    if (target.checked) {
                      onChange(selected.concat([version]).sort((a, b) => b - a));
                    } else {
                      onChange(selected.filter((selected) => selected !== version));
                    }
                  }}
                />
              );
            })}
          </Form.Group>
        </Form.Group>
      </Form>
    </Popover>
  );
}

function usesSpecificVersions(selection) {
  return !(selection.length === 1 && (selection[0] === 'all' || selection[0] === 'latest'));
}
