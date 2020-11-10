/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useMemo, useCallback} from 'react';
import debounce from 'debounce';

import {MultiSelect} from 'components';
import {t} from 'translation';

import {searchIdentities} from './service';

import './MultiUserInput.scss';

export default function MultiUserInput({
  users = [],
  collectionUsers = [],
  onAdd,
  onRemove,
  onClear,
}) {
  const [loading, setLoading] = useState(false);
  const [hasMore, setHasMore] = useState(false);
  const [initialDataLoaded, setInitialDataLoaded] = useState(false);
  const [empty, setEmpty] = useState(true);
  const [identities, setIdentities] = useState([]);

  const search = useMemo(
    () =>
      debounce(async (query) => {
        const {total, result} = await searchIdentities(query);
        setIdentities(result);
        setLoading(false);
        setHasMore(total > result.length);
        setEmpty(result.length === 0);
        setInitialDataLoaded(!query);
      }, 800),
    []
  );

  const cancelPendingSearch = useCallback(() => {
    search.clear();
    setLoading(false);
  }, [search]);

  const loadNewValues = useCallback(
    (query) => {
      if (initialDataLoaded && !query) {
        return cancelPendingSearch();
      }
      setLoading(true);
      search(query);
    },
    [cancelPendingSearch, initialDataLoaded, search]
  );

  useEffect(() => {
    setLoading(true);
    search('');
  }, [search]);

  function handleClose() {
    // prevents unnecessary requests
    if (loading && !empty) {
      cancelPendingSearch();
    } else if (empty) {
      // prevents disabling the typeahead if closed empty
      setLoading(true);
    }
  }

  function add(id) {
    if (id) {
      const selectedIdentity = identities
        .filter(filterSelected)
        .find((identity) => identity.id === id);
      if (selectedIdentity) {
        onAdd(selectedIdentity);
      } else {
        onAdd({id});
      }
    }
  }

  const filterSelected = ({id, type}) => {
    const exists = (users) => users.some((user) => user.id === `${type.toUpperCase()}:${id}`);

    return !exists(users) && !exists(collectionUsers);
  };

  return (
    <MultiSelect
      values={users.map((user) => ({
        value: user.id,
        label: formatTypeaheadOption(user.identity).text,
      }))}
      className="MultiUserInput"
      onSearch={loadNewValues}
      loading={loading}
      hasMore={!loading && hasMore}
      onClose={handleClose}
      onOpen={(query) => loadNewValues(query)}
      placeholder={t('common.collection.addUserModal.searchPlaceholder')}
      onAdd={add}
      onRemove={onRemove}
      onClear={onClear}
      async
      typedOption
    >
      {identities.filter(filterSelected).map((identity) => {
        const {text, tag, subTexts} = formatTypeaheadOption(identity);
        return (
          <MultiSelect.Option key={identity.id} value={identity.id} label={text}>
            <MultiSelect.Highlight>{text}</MultiSelect.Highlight>
            {tag}
            {subTexts && (
              <span className="subTexts">
                {subTexts
                  .filter((subText) => subText)
                  .map((subText, i) => (
                    <span className="subText" key={i}>
                      <MultiSelect.Highlight matchFromStart>{subText}</MultiSelect.Highlight>
                    </span>
                  ))}
              </span>
            )}
          </MultiSelect.Option>
        );
      })}
    </MultiSelect>
  );
}

function formatTypeaheadOption({name, email, id, type}) {
  const subTexts = [];
  if (name) {
    subTexts.push(email);
  }

  if (name || email) {
    subTexts.push(id);
  }

  return {
    text: name || email || id,
    tag: type === 'group' && ` (${t('common.user-group.label')})`,
    subTexts,
  };
}
