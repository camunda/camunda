/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ReactNode} from 'react';
import classnames from 'classnames';

import {t} from 'translation';
import {Popover, LabeledInput, Form, Badge, LoadingIndicator} from 'components';

import {Version} from './service';

import './VersionPopover.scss';

interface VersionPopoverProps {
  versions?: Version[];
  selected: Version['version'][];
  selectedSpecificVersions?: Version['version'][];
  onChange: (versions: Version['version'][]) => void;
  disabled?: boolean;
  tooltip?: ReactNode;
  loading?: boolean;
  useCarbonTrigger?: boolean;
  label?: ReactNode;
}

export default function VersionPopover({
  versions,
  selected,
  selectedSpecificVersions = [],
  onChange,
  disabled,
  tooltip,
  loading,
  useCarbonTrigger,
  label,
}: VersionPopoverProps) {
  const specific = usesSpecificVersions(selected);

  let title = t('common.none');
  if (selected.length === 1 && selected[0] === 'all') {
    title = t('common.all');
  } else if (selected.length === 1 && selected[0] === 'latest') {
    title = versions
      ? t('common.definitionSelection.latest') + ' : ' + versions[0]?.version
      : t('common.definitionSelection.latest');
  } else if (selected.length) {
    title = selected.join(', ');
  }

  return (
    <Popover
      className="VersionPopover"
      tooltip={tooltip}
      title={title}
      disabled={disabled || !versions}
      align="bottom-right"
      useCarbonTrigger={useCarbonTrigger}
      label={label}
    >
      {loading && <LoadingIndicator />}
      <Form compact>
        <Form.Group>
          <LabeledInput
            label={t('common.all')}
            type="radio"
            checked={selected[0] === 'all'}
            onChange={() => onChange(['all'])}
            disabled={loading}
          />
          <LabeledInput
            label={t('common.definitionSelection.version.alwaysLatest')}
            type="radio"
            checked={selected[0] === 'latest'}
            onChange={() => onChange(['latest'])}
            disabled={loading}
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
            disabled={loading}
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
                  disabled={!specific || loading}
                  onChange={({target}) => {
                    if (target.checked) {
                      onChange(selected.concat([version]).sort((a, b) => Number(b) - Number(a)));
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

function usesSpecificVersions(selectedVersions: Version['version'][]) {
  return !(
    selectedVersions.length === 1 &&
    (selectedVersions[0] === 'all' || selectedVersions[0] === 'latest')
  );
}
