/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {CopyToClipboard} from 'components';
import {ProcessFilter} from 'types';
import {
  Checkbox,
  TextInput,
  Toggle,
  Form,
  Stack,
  ToggleSkeleton,
  TextInputSkeleton,
  ButtonSkeleton,
  ButtonSet,
} from '@carbon/react';

import {t} from 'translation';
import {addNotification} from 'notifications';

import './ShareEntity.scss';

interface ShareEntityProps {
  getSharedEntity: (resourceId: string) => Promise<string>;
  shareEntity: (resourceId: string) => Promise<string>;
  revokeEntitySharing: (id: string) => Promise<void>;
  resourceId: string;
  type: string;
  filter?: ProcessFilter[];
  defaultFilter?: ProcessFilter;
}

export default function ShareEntity({
  getSharedEntity,
  shareEntity,
  revokeEntitySharing,
  resourceId,
  type,
  filter,
  defaultFilter,
}: ShareEntityProps) {
  const [loaded, setLoaded] = useState(false);
  const [isShared, setIsShared] = useState(false);
  const [includeFilters, setIncludeFilters] = useState(false);
  const [id, setId] = useState('');

  useEffect(() => {
    const fetchData = async () => {
      const sharedId = await getSharedEntity(resourceId);
      setId(sharedId);
      setIsShared(!!sharedId);
      setLoaded(true);
    };
    fetchData();
  }, [getSharedEntity, resourceId]);

  const toggleValue = async (checked: boolean) => {
    setIsShared(checked);

    if (checked) {
      const sharedId = await shareEntity(resourceId);
      setId(sharedId);
    } else {
      await revokeEntitySharing(id);
      setId('');
    }
  };

  const buildShareLink = (params: {mode?: string} = {}) => {
    if (!id) {
      return '';
    }

    const currentUrl = window.location.href;
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      query.set(key, value);
    }
    if (includeFilters) {
      query.set('filter', JSON.stringify(filter));
    } else if (defaultFilter) {
      query.set('filter', JSON.stringify(defaultFilter));
    }
    const queryString = query.toString();

    return `${currentUrl.substring(0, currentUrl.indexOf('#'))}external/#/share/${type}/${id}${
      queryString && '?' + queryString
    }`;
  };

  const buildShareLinkForEmbedding = () => {
    if (id) {
      return `<iframe src="${buildShareLink({
        mode: 'embed',
      })}" frameborder="0" style="width: 1000px; height: 700px; allowtransparency; overflow: scroll"></iframe>`;
    } else {
      return '';
    }
  };

  const showCopyMessage = () => {
    addNotification(t('common.sharing.notification').toString());
  };

  if (!loaded) {
    return (
      <div className="ShareEntity">
        <Stack gap={6}>
          <ToggleSkeleton />
          <TextInputSkeleton />
          <div>
            <ButtonSkeleton />
            <ButtonSkeleton />
          </div>
        </Stack>
      </div>
    );
  }

  return (
    <Form className="ShareEntity">
      <Stack gap={6}>
        <Toggle
          id="sharingToggle"
          toggled={isShared}
          size="sm"
          labelText={t('common.sharing.popoverTitle').toString()}
          onToggle={toggleValue}
        />
        <TextInput
          id="sharingLinkText"
          placeholder={t('common.sharing.inputPlaceholder').toString()}
          labelText={t('common.link')}
          value={buildShareLink()}
          disabled={!isShared}
          readOnly
        />
        {filter && (
          <Checkbox
            id="shareFilterCheckbox"
            className="shareFilterCheckbox"
            disabled={!isShared}
            checked={includeFilters}
            onChange={(evt) => {
              setIncludeFilters(evt.target.checked);
            }}
            labelText={t('common.sharing.filtersLabel')}
          />
        )}

        <ButtonSet aria-disabled={!isShared}>
          <CopyToClipboard disabled={!isShared} value={buildShareLink()} onCopy={showCopyMessage}>
            {t('common.sharing.copyLabel')}
          </CopyToClipboard>
          <CopyToClipboard
            disabled={!isShared}
            value={buildShareLinkForEmbedding()}
            onCopy={showCopyMessage}
            kind="secondary"
          >
            {t('common.sharing.embedLabel')}
          </CopyToClipboard>
        </ButtonSet>
      </Stack>
    </Form>
  );
}
