/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useCallback, useEffect, useState, useMemo} from 'react';
import debounce from 'debounce';

import {Icon, Input, Table} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import {loadIngestedEvents} from './service';

import './IngestedEvents.scss';

const initialOffset = 0;
const initialLimit = 20;

export function IngestedEvents({mightFail}) {
  const [eventsResponse, setEventsResponse] = useState({results: []});
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState('timestamp');
  const [sortOrder, setSortOrder] = useState('desc');

  const loadEvents = useCallback(
    async (payload) => {
      setLoading(true);
      await mightFail(loadIngestedEvents(payload), setEventsResponse, showError);
      setLoading(false);
    },
    [mightFail]
  );

  const fetchData = useCallback(
    async ({pageSize, pageIndex}) => {
      const offset = pageSize * pageIndex;
      const payload = {limit: pageSize, offset, sortBy, sortOrder};
      if (searchTerm) {
        payload.searchTerm = searchTerm;
      }

      loadEvents(payload);
    },
    [loadEvents, searchTerm, sortBy, sortOrder]
  );

  useEffect(() => {
    loadEvents({limit: initialLimit, offset: initialOffset});
  }, [loadEvents]);

  const search = useMemo(() => debounce(async (query) => setSearchTerm(query), 500), []);

  useEffect(() => {
    search(query);
  }, [query, search]);

  const headerKeys = Object.keys(eventsResponse.results[0] || {});
  const bodyData = eventsResponse.results.map((event) => Object.values(event));

  return (
    <div className="IngestedEvents">
      <h1 className="title">{t('events.ingested.eventSources')}</h1>
      <div className="header">
        <h4 className="tableTitle">{t('events.ingested.label')}</h4>
        <div className="searchInputContainer">
          <Input
            value={query}
            className="searchInput"
            placeholder={t('events.ingested.search')}
            type="text"
            onChange={(evt) => setQuery(evt.target.value)}
            onClear={() => setQuery('')}
          />
          <Icon className="searchIcon" type="search" size="20" />
        </div>
      </div>
      <Table
        head={headerKeys.map((key) => ({
          label: t('events.ingested.' + key),
          id: key,
          sortable: key !== 'id',
        }))}
        body={bodyData}
        fetchData={fetchData}
        loading={loading}
        defaultPageSize={eventsResponse.limit}
        totalEntries={eventsResponse.total}
        sorting={{by: sortBy, order: sortOrder}}
        updateSorting={(by, order) => {
          setSortBy(by);
          setSortOrder(order);
        }}
      />
    </div>
  );
}

export default withErrorHandling(IngestedEvents);
