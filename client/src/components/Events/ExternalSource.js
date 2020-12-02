/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Labeled, Typeahead, DocsLink} from 'components';
import {t} from 'translation';

import './ExternalSource.scss';

export default function ExternalSource({empty}) {
  if (empty) {
    return (
      <div className="ExternalSource empty">
        {t('events.table.seeDocs')}
        <DocsLink location="technical-guide/rest-api/event-ingestion/">
          {t('events.table.documentation')}
        </DocsLink>
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
