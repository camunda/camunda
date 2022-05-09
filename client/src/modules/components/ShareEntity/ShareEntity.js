/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {CopyToClipboard, Switch, Input, LabeledInput, LoadingIndicator, Form} from 'components';

import './ShareEntity.scss';
import {t} from 'translation';
import {addNotification} from 'notifications';

export default class ShareEntity extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      loaded: false,
      isShared: false,
      includeFilters: false,
      id: '',
    };
  }

  componentDidMount = async () => {
    const id = await this.props.getSharedEntity(this.props.resourceId);
    this.setState({
      id,
      isShared: !!id,
      loaded: true,
    });
  };

  toggleValue = async ({target: {checked}}) => {
    this.setState({
      isShared: checked,
    });

    if (checked) {
      const id = await this.props.shareEntity(this.props.resourceId);
      this.setState({id});
    } else {
      await this.props.revokeEntitySharing(this.state.id);
      this.setState({id: ''});
    }
  };

  buildShareLink = (params = {}) => {
    if (!this.state.id) {
      return '';
    }

    const currentUrl = window.location.href;
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      query.set(key, value);
    }
    if (this.state.includeFilters) {
      query.set('filter', JSON.stringify(this.props.filter));
    } else if (this.props.defaultFilter) {
      query.set('filter', JSON.stringify(this.props.defaultFilter));
    }
    const queryString = query.toString();

    return `${currentUrl.substring(0, currentUrl.indexOf('#'))}external/#/share/${
      this.props.type
    }/${this.state.id}${queryString && '?' + queryString}`;
  };

  buildShareLinkForEmbedding = () => {
    if (this.state.id) {
      return `<iframe src="${this.buildShareLink({
        mode: 'embed',
      })}" frameborder="0" style="width: 1000px; height: 700px; allowtransparency; overflow: scroll"></iframe>`;
    } else {
      return '';
    }
  };

  disabled = () => {
    return !this.state.isShared;
  };

  showCopyMessage = () => {
    addNotification(t('common.sharing.notification'));
  };

  render() {
    const {loaded, isShared, includeFilters} = this.state;

    if (!loaded) {
      return (
        <div className="ShareEntity">
          <LoadingIndicator />
        </div>
      );
    }

    return (
      <Form className="ShareEntity">
        <Switch
          checked={isShared}
          onChange={this.toggleValue}
          label={t('common.sharing.popoverTitle')}
        />
        <Input
          className="linkText"
          readOnly
          disabled={!isShared}
          value={this.buildShareLink()}
          placeholder={t('common.sharing.inputPlaceholder')}
        />
        <div className="includeFilters">
          <LabeledInput
            type="checkbox"
            checked={includeFilters}
            onChange={(evt) => {
              this.setState({includeFilters: evt.target.checked});
            }}
            label={t('common.sharing.filtersLabel')}
          />
        </div>
        <div className="clipboardButtons">
          <CopyToClipboard
            disabled={!isShared}
            value={this.buildShareLink()}
            onCopy={this.showCopyMessage}
          >
            {t('common.sharing.copyLabel')}
          </CopyToClipboard>
          <CopyToClipboard
            disabled={!isShared}
            value={this.buildShareLinkForEmbedding()}
            onCopy={this.showCopyMessage}
          >
            {t('common.sharing.embedLabel')}
          </CopyToClipboard>
        </div>
      </Form>
    );
  }
}
