/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect, useMemo} from 'react';
import debounce from 'debounce';
import classnames from 'classnames';

import {Button, Checklist, DocsLink, LabeledInput} from 'components';
import {t} from 'translation';
import debouncePromise from 'debouncePromise';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import {loadExternalGroups} from './service';

import './ExternalSource.scss';

const debounceRequest = debouncePromise();
const externalSource = {type: 'external', configuration: {includeAllGroups: true, group: null}};
const pageSize = 10;

export function ExternalSource({
  empty,
  mightFail,
  onChange,
  externalSources,
  existingExternalSources,
}) {
  const [availableValues, setAvailableValues] = useState([]);
  const [valuesToLoad, setValuesToLoad] = useState(pageSize);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [query, setQuery] = useState('');
  const [hasMore, setHasMore] = useState(false);

  const search = useMemo(() => debounce((query) => setSearchTerm(query), 500), []);

  useEffect(() => {
    setLoading(true);
    setValuesToLoad(pageSize);
    search(query);
  }, [query, search]);

  useEffect(() => {
    (async () => {
      setLoading(true);
      await debounceRequest(
        mightFail,
        0,
        loadExternalGroups({searchTerm, limit: valuesToLoad + 1}),
        (groups) => {
          setAvailableValues(groups.slice(0, valuesToLoad));
          setHasMore(groups.length > valuesToLoad);
        },
        showError
      );
      setLoading(false);
    })();
  }, [mightFail, searchTerm, valuesToLoad]);

  if (empty) {
    return (
      <div className="ExternalSource empty">
        {t('events.table.seeDocs')}
        <DocsLink location="apis-clients/optimize-api/event-ingestion/">
          {t('events.table.documentation')}
        </DocsLink>
        .
      </div>
    );
  }

  const toggleAllEventsGroup = ({target: {checked}}) => onChange(checked ? [externalSource] : []);

  const selectAllExists = existingExternalSources.some((src) => src.configuration.includeAllGroups);
  const selectAll =
    externalSources.some((src) => src.configuration.includeAllGroups) || selectAllExists;
  const selectedGroups = externalSources
    .filter((src) => !src.configuration.includeAllGroups)
    .map((src) => src.configuration.group);

  return (
    <div className="ExternalSource">
      <Checklist
        customHeader={t('events.sources.eventGroups')}
        preItems={
          !loading &&
          !query && (
            <LabeledInput
              className={classnames({highlight: selectAll && !selectAllExists})}
              checked={selectAll}
              disabled={selectAllExists}
              type="checkbox"
              label={t('events.sources.allInOne')}
              onChange={toggleAllEventsGroup}
            />
          )
        }
        selectedItems={selectedGroups}
        allItems={availableValues}
        onSearch={setQuery}
        onChange={(selected) =>
          onChange(
            selected.map((group) => ({
              type: 'external',
              configuration: {includeAllGroups: false, group},
            }))
          )
        }
        loading={loading}
        formatter={(values, selectedValues) =>
          values.map((value) => {
            const existingGroup = existingExternalSources.some(
              (src) => !src.configuration.includeAllGroups && src.configuration.group === value
            );

            return {
              id: value,
              label: formatGroup(value),
              checked: selectAll || existingGroup || selectedValues.includes(value),
              disabled: selectAll || existingGroup,
            };
          })
        }
        labels={{
          search: t('events.sources.search'),
          empty: t('events.sources.noGroups'),
        }}
      />
      {!loading && hasMore && (
        <Button className="loadMore" onClick={() => setValuesToLoad(valuesToLoad + pageSize)} link>
          {t('common.filter.variableModal.loadMore')}
        </Button>
      )}
    </div>
  );
}

export default withErrorHandling(ExternalSource);

function formatGroup(val) {
  return val === null ? t('events.sources.ungrouped') : val;
}
