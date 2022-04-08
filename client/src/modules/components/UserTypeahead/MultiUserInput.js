/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {MultiSelect} from 'components';
import {t} from 'translation';
import debouncePromise from 'debouncePromise';

import {searchIdentities} from './service';

import './MultiUserInput.scss';

const debounceRequest = debouncePromise();

export default function MultiUserInput({
  users = [],
  collectionUsers = [],
  onAdd,
  fetchUsers,
  optionsOnly,
  onRemove,
  onClear,
  excludeGroups = false,
}) {
  const [loading, setLoading] = useState(true);
  const [hasMore, setHasMore] = useState(false);
  const [identities, setIdentities] = useState([]);

  const loadNewValues = async (query, delay = 0) => {
    setLoading(true);

    const {total, result} = await debounceRequest(async () => {
      return await (fetchUsers || searchIdentities)(query, excludeGroups);
    }, delay);

    setIdentities(result);
    setLoading(false);
    setHasMore(total > result.length);
  };

  function add(id) {
    if (id || id === null) {
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
      onSearch={(query) => loadNewValues(query, 800)}
      loading={loading}
      hasMore={!loading && hasMore}
      onOpen={loadNewValues}
      onClose={() => setLoading(true)}
      placeholder={t('common.collection.addUserModal.searchPlaceholder')}
      onAdd={add}
      onRemove={onRemove}
      onClear={onClear}
      async
      typedOption={!optionsOnly}
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
