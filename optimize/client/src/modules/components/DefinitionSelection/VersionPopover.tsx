/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps, InputHTMLAttributes, ReactNode} from 'react';
import classnames from 'classnames';
import {
  Checkbox,
  CheckboxSkeleton,
  Form,
  FormGroup,
  RadioButton,
  RadioButtonGroup,
  RadioButtonSkeleton,
  Tag,
} from '@carbon/react';

import {t} from 'translation';
import {Popover} from 'components';

import {Version} from './service';

import './VersionPopover.scss';

interface VersionPopoverProps extends Pick<ComponentProps<typeof Popover>, 'align'> {
  versions?: Version[];
  selected: Version['version'][];
  selectedSpecificVersions?: Version['version'][];
  onChange: (versions: Version['version'][]) => void;
  disabled?: boolean;
  loading?: boolean;
  label?: ReactNode;
}

export default function VersionPopover({
  versions,
  selected,
  selectedSpecificVersions = [],
  onChange,
  disabled,
  loading,
  label,
  align = 'bottom-right',
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
      align={align}
      trigger={
        <Popover.ListBox label={label} disabled={disabled || !versions}>
          {title}
        </Popover.ListBox>
      }
    >
      <Form>
        <FormGroup legendText={t('common.definitionSelection.version.label')}>
          <RadioButtonGroup name="version-selection" orientation="vertical">
            <ReplaceContentOnLoading loading={loading} loadingComponent={RadioButtonSkeleton}>
              <RadioButton
                id="all"
                value="all"
                labelText={t('common.all')}
                checked={selected[0] === 'all'}
                onClick={() => onChange(['all'])}
              />
              <RadioButton
                id="latest"
                value="latest"
                labelText={t('common.definitionSelection.version.alwaysLatest')}
                checked={selected[0] === 'latest'}
                onClick={() => onChange(['latest'])}
              />
              <RadioButton
                id="specific"
                value="specific"
                labelText={t(
                  `common.definitionSelection.version.specific.label${
                    versions?.length === 1 ? '' : '-plural'
                  }`
                )}
                checked={specific}
                onClick={() => onChange(selectedSpecificVersions)}
              />
            </ReplaceContentOnLoading>
          </RadioButtonGroup>
          <FormGroup
            legendText={t('common.definitionSelection.version.specific.label')}
            className={classnames('specificVersions', {
              disabled: !specific,
            })}
          >
            <ReplaceContentOnLoading
              loading={loading}
              count={versions?.length}
              loadingComponent={CheckboxSkeleton}
            >
              {versions?.map(({version, versionTag}) => {
                return (
                  <Checkbox
                    key={version}
                    id={version}
                    labelText={
                      <span className="checkboxLabel">
                        {version}
                        {versionTag && <Tag size="sm">{versionTag}</Tag>}
                      </span>
                    }
                    checked={selectedSpecificVersions.includes(version)}
                    disabled={!specific}
                    onChange={(_, {checked}) => {
                      if (checked) {
                        onChange(selected.concat([version]).sort((a, b) => Number(b) - Number(a)));
                      } else {
                        onChange(selected.filter((selected) => selected !== version));
                      }
                    }}
                  />
                );
              })}
            </ReplaceContentOnLoading>
          </FormGroup>
        </FormGroup>
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

function ReplaceContentOnLoading<P extends InputHTMLAttributes<HTMLInputElement>>({
  loading,
  children,
  count = 3,
  loadingComponent: LoadingComponent,
}: {
  loading?: boolean;
  children: ReactNode;
  count?: number;
  loadingComponent: (props: P) => JSX.Element;
}) {
  if (!loading) {
    return children;
  }

  return Array.from({length: count}, (_, i) => (
    <LoadingComponent key={i} className="skeleton" {...({} as P)} />
  ));
}
