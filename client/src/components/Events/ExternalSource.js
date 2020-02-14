/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import {getOptimizeVersion} from 'config';
import {Labeled, Typeahead} from 'components';
import {t} from 'translation';
import './ExternalSource.scss';

export default function ExternalSource({empty}) {
  const [version, setVersion] = useState('latest');

  useEffect(() => {
    (async () => {
      if (empty) {
        const version = (await getOptimizeVersion()).split('.');
        version.length = 2;
        setVersion(version.join('.'));
      }
    })();
  }, [empty]);

  if (empty) {
    return (
      <div className="ExternalSource empty">
        {t('events.table.seeDocs')}
        <a
          href={`https://docs.camunda.org/optimize/${version}/technical-guide/setup/configuration/#ingestion-configuration`}
          target="_blank"
          rel="noopener noreferrer"
        >
          {t('events.table.documentation')}
        </a>
        .
      </div>
    );
  }

  return (
    <div className="ExternalSource">
      <Labeled label={t('events.sources.selectExternal')}>
        <Typeahead initialValue="all">
          <Typeahead.Option value="all">{t('events.sources.allExternal')}</Typeahead.Option>
        </Typeahead>
      </Labeled>
    </div>
  );
}
