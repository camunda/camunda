/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import debounce from 'debounce';
import {searchIdentities} from './service';
import {Typeahead} from 'components';
import {t} from 'translation';
import './UserTypeahead.scss';

export default class UserTypeahead extends React.Component {
  state = {
    loading: false,
    hasMore: false,
    initialDataLoaded: false,
    empty: false,
    identities: []
  };

  componentDidMount() {
    this.loadNewValues('');
  }

  loadNewValues = query => {
    if (this.state.initialDataLoaded && !query) {
      return this.cancelPendingSearch();
    }
    this.setState({loading: true});
    this.search(query);
  };

  search = debounce(async query => {
    const {total, result} = await searchIdentities(query);
    this.setState({
      identities: result,
      loading: false,
      hasMore: total > result.length,
      empty: result.length === 0,
      initialDataLoaded: !query
    });
  }, 800);

  cancelPendingSearch = () => {
    this.search.clear();
    this.setState({loading: false});
  };

  handleClose = () => {
    const {empty, loading} = this.state;
    // prevents unnecessary requests
    if (loading) {
      this.cancelPendingSearch();
    } else if (empty) {
      // prevents disabling the typeahead if closed empty
      this.setState({loading: true});
    }
  };

  selectIdentity = id => {
    const selectedIdentity = this.state.identities.find(identity => identity.id === id);
    if (selectedIdentity) {
      this.props.onChange(selectedIdentity);
    } else {
      this.props.onChange({id});
    }
  };

  render() {
    const {loading, hasMore, identities} = this.state;
    return (
      <Typeahead
        className="UserTypeahead"
        onSearch={this.loadNewValues}
        loading={loading}
        hasMore={!loading && hasMore}
        onClose={this.handleClose}
        onOpen={() => this.loadNewValues('')}
        placeholder={t('common.collection.addUserModal.searchPlaceholder')}
        onChange={this.selectIdentity}
        async
        typedOption
      >
        {identities.map(identity => {
          const {text, tag, subTexts} = formatTypeaheadOption(identity);
          return (
            <Typeahead.Option key={identity.id} value={identity.id} label={text}>
              <Typeahead.Highlight>{text}</Typeahead.Highlight>
              {tag}
              {subTexts && (
                <span className="subTexts">
                  {subTexts
                    .filter(subText => subText)
                    .map((subText, i) => (
                      <span className="subText" key={i}>
                        <Typeahead.Highlight matchFromStart>{subText}</Typeahead.Highlight>
                      </span>
                    ))}
                </span>
              )}
            </Typeahead.Option>
          );
        })}
      </Typeahead>
    );
  }
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
    tag: type === 'group' && ' (User Group)',
    subTexts
  };
}
