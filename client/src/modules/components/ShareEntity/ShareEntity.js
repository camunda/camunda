/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {CopyToClipboard, Switch, Icon, LoadingIndicator, Form} from 'components';

import './ShareEntity.scss';

export default class ShareEntity extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      loaded: false,
      isShared: false,
      id: ''
    };
  }

  componentDidMount = async () => {
    const id = await this.props.getSharedEntity(this.props.resourceId);
    this.setState({
      id,
      isShared: !!id,
      loaded: true
    });
  };

  toggleValue = async ({target: {checked}}) => {
    this.setState({
      isShared: checked
    });

    if (checked) {
      const id = await this.props.shareEntity(this.props.resourceId);
      this.setState({id});
    } else {
      await this.props.revokeEntitySharing(this.state.id);
      this.setState({id: ''});
    }
  };

  buildShareLink = () => {
    if (this.state.id) {
      return `${window.location.origin}/#/share/${this.props.type}/${this.state.id}`;
    } else {
      return '';
    }
  };

  buildShareLinkForEmbedding = () => {
    if (this.state.id) {
      return `<iframe src="${this.buildShareLink()}" frameborder="0" style="width: 1000px; height: 700px; allowtransparency; overflow: scroll"></iframe>`;
    } else {
      return '';
    }
  };

  disabled = () => {
    return !this.state.isShared;
  };

  render() {
    if (!this.state.loaded) {
      return (
        <div className="ShareEntity">
          <LoadingIndicator />
        </div>
      );
    }

    return (
      <Form className="ShareEntity">
        <div className="enable">
          <div className="enableText">Enable sharing </div>
          <Switch checked={this.state.isShared} onChange={this.toggleValue} />
        </div>
        <div className={'linkArea' + (this.disabled() ? 'Disabled' : '')}>
          <div className="clipboard">
            <Icon type="link" renderedIn="span" />
            <span className="label">Link</span>
            <span className="labelDescription">{`Use the following URL to share the ${
              this.props.type
            }
                with people who don't have a Camunda Optimize account:`}</span>
            <CopyToClipboard
              className="shareLink"
              disabled={this.disabled()}
              value={this.buildShareLink()}
            />
          </div>
          <div className="clipboard">
            <Icon type="embed" renderedIn="span" />
            <span className="label">Embed</span>
            <span className="labelDescription">{`Use the following HTML code to embed the ${
              this.props.type
            } into blogs and web pages:`}</span>
            <CopyToClipboard
              className="embedLink"
              disabled={this.disabled()}
              value={this.buildShareLinkForEmbedding()}
            />
          </div>
        </div>
      </Form>
    );
  }
}
