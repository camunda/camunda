/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useCallback, useEffect, useState, useMemo} from 'react';
import debounce from 'debounce';
import {TableSelectRow, TableSelectAll} from '@carbon/react';

import {
  Deleter,
  DocsLink,
  Dropdown,
  Icon,
  PageTitle,
  SearchInput,
  Table,
  Tooltip,
} from 'components';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';
import debouncePromise from 'debouncePromise';

import {deleteEvents, loadIngestedEvents} from './service';

import './IngestedEvents.scss';

const debounceRequest = debouncePromise();

const initialOffset = 0;
const initialLimit = 20;

export function IngestedEvents({mightFail}) {
  const [eventsResponse, setEventsResponse] = useState({results: []});
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState('timestamp');
  const [sortOrder, setSortOrder] = useState('desc');
  const [selected, setSelected] = useState([]);
  const [deleting, setDeleting] = useState(false);

  const loadEvents = useCallback(
    async (payload = {limit: initialLimit, offset: initialOffset}) => {
      setLoading(true);
      await debounceRequest(
        mightFail,
        0,
        loadIngestedEvents(payload),
        setEventsResponse,
        showError
      );
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
    loadEvents();
  }, [loadEvents]);

  const search = useMemo(() => debounce(async (query) => setSearchTerm(query), 500), []);

  useEffect(() => {
    search(query);
  }, [query, search]);

  const headerKeys = Object.keys(eventsResponse.results[0] || {});
  const currentViewIds = eventsResponse.results.map(({id}) => id);
  const allSelectedInView =
    currentViewIds.length > 0 && currentViewIds.every((id) => selected.includes(id));
  const someSelectedInView =
    !allSelectedInView && currentViewIds.some((id) => selected.includes(id));
  const maxDeletionReached = selected.length > 1000;

  const head = headerKeys.map((key) => ({
    label: t('events.ingested.' + key),
    id: key,
    sortable: key !== 'id',
  }));

  if (head.length) {
    head.unshift({
      label: (
        <TableSelectAll
          id="checked"
          key="checked"
          name="checked"
          ariaLabel="checked"
          indeterminate={someSelectedInView}
          checked={allSelectedInView}
          onSelect={({target: {checked}}) =>
            checked
              ? setSelected([...new Set([...selected, ...currentViewIds])])
              : setSelected(selected.filter((id) => !currentViewIds.includes(id)))
          }
        />
      ),
      id: 'selectedAll',
      sortable: false,
      width: 50,
    });
  }

  return (
    <div className="IngestedEvents">
      <PageTitle pageName={t('events.ingested.label')} />
      <h1 className="title">{t('events.ingested.eventSources')}</h1>
      <div className="header">
        <h4 className="tableTitle">{t('events.ingested.label')}</h4>
        <SearchInput
          value={query}
          placeholder={t('events.ingested.search')}
          onChange={(evt) => setQuery(evt.target.value)}
          onClear={() => setQuery('')}
        />
        {selected.length > 0 && (
          <Dropdown
            className="selectionActions"
            primary
            label={selected.length + ' ' + t('common.selected')}
          >
            <Tooltip
              content={maxDeletionReached ? t('events.ingested.deleteLimitReached') : undefined}
            >
              <Dropdown.Option onClick={() => setDeleting(true)} disabled={maxDeletionReached}>
                <Icon type="delete" />
                {t('common.delete')}
              </Dropdown.Option>
            </Tooltip>
          </Dropdown>
        )}
      </div>
      <Table
        head={head}
        body={eventsResponse.results.map((event) => [
          <TableSelectRow
            id={event.id}
            name={event.eventName}
            ariaLabel={event.eventName}
            checked={selected.includes(event.id)}
            onSelect={({target: {checked}}) =>
              checked
                ? setSelected([...selected, event.id])
                : setSelected(selected.filter((id) => id !== event.id))
            }
          />,
          ...Object.values(event),
        ])}
        fetchData={fetchData}
        loading={loading}
        defaultPageSize={eventsResponse.limit}
        totalEntries={eventsResponse.total}
        sorting={{by: sortBy, order: sortOrder}}
        updateSorting={(by, order) => {
          setSortBy(by);
          setSortOrder(order);
        }}
        noData={
          <>
            {t('events.ingested.noData')}{' '}
            <DocsLink location="apis-clients/optimize-api/event-ingestion/">
              {t('events.sources.learnMore')}
            </DocsLink>
          </>
        }
      />
      <Deleter
        type="ingestedEvents"
        entity={deleting}
        deleteEntity={() =>
          mightFail(
            deleteEvents(selected),
            () => {
              setSelected([]);
              loadEvents();
            },
            showError
          )
        }
        onClose={() => setDeleting(false)}
        deleteButtonText={t('common.delete')}
        descriptionText={t('events.ingested.deleteWarning')}
      />
    </div>
  );
}

export default withErrorHandling(IngestedEvents);
