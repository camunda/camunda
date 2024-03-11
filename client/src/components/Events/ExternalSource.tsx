/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState, useEffect, useMemo} from 'react';
import {TableSelectRow} from '@carbon/react';
import debounce from 'debounce';
import classnames from 'classnames';

import {Button, Checklist, DocsLink} from 'components';
import {t} from 'translation';
import debouncePromise from 'debouncePromise';
import {useErrorHandling} from 'hooks';
import {showError} from 'notifications';

import {loadExternalGroups} from './service';

import './ExternalSource.scss';

type Source = {
  type: string;
  configuration: {
    includeAllGroups?: boolean;
    group: string | null;
  };
};

const debounceRequest = debouncePromise();
const externalSource: Source = {
  type: 'external',
  configuration: {includeAllGroups: true, group: null},
};
const pageSize = 10;

interface ExternalSourceProps {
  empty: boolean;
  externalSources: Source[];
  existingExternalSources: Source[];
  onChange: (sources: (Source | null)[]) => void;
}

export default function ExternalSource({
  empty,
  onChange,
  externalSources,
  existingExternalSources,
}: ExternalSourceProps) {
  const [availableValues, setAvailableValues] = useState<string[]>([]);
  const [valuesToLoad, setValuesToLoad] = useState(pageSize);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [query, setQuery] = useState('');
  const [hasMore, setHasMore] = useState(false);
  const {mightFail} = useErrorHandling();

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
        (groups: string[]) => {
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

  const toggleAllEventsGroup = <T extends {target: EventTarget}>({target}: T) => {
    const {checked} = target as HTMLInputElement;
    onChange(checked ? [externalSource] : []);
  };
  const selectAllExists = existingExternalSources.some((src) => src.configuration.includeAllGroups);
  const selectAll =
    externalSources.some((src) => src.configuration.includeAllGroups) || selectAllExists;
  const selectedGroups = externalSources
    .filter((src) => !src.configuration.includeAllGroups)
    .map((src) => src.configuration.group);

  return (
    <div className="ExternalSource">
      <Checklist<string | null>
        customHeader={t('events.sources.eventGroups')}
        preItems={
          !loading && !query
            ? {
                content: [
                  <TableSelectRow
                    id="selectAll"
                    name="selectAll"
                    ariaLabel={t('events.sources.allInOne').toString()}
                    className={classnames({highlight: selectAll && !selectAllExists})}
                    checked={selectAll}
                    disabled={selectAllExists}
                    onSelect={toggleAllEventsGroup}
                  />,
                  t('events.sources.allInOne'),
                ],
                props: {
                  onClick: () =>
                    toggleAllEventsGroup({target: {checked: !selectAll} as HTMLInputElement}),
                },
              }
            : undefined
        }
        selectedItems={selectedGroups}
        allItems={availableValues}
        onSearch={setQuery}
        onChange={(selected) =>
          onChange(
            selected.map((group) => ({
              type: 'external',
              configuration: {includeAllGroups: false, group: group || null},
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

function formatGroup(val: string | null) {
  return val === null ? t('events.sources.ungrouped') : val;
}
