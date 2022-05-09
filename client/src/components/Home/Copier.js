/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Redirect} from 'react-router-dom';

import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import CopyModal from './modals/CopyModal';
import {copyEntity} from './service';

export default withErrorHandling(
  class Copier extends React.Component {
    state = {
      redirect: null,
    };

    copy = (name, redirect, destination) => {
      const {
        entity: {entityType, id},
        onCopy,
      } = this.props;

      this.props.mightFail(
        copyEntity(entityType, id, name, destination),
        (newId) => {
          if (redirect) {
            if (entityType === 'collection') {
              this.setState({redirect: `/collection/${newId}/`});
            } else {
              this.setState({redirect: destination ? `/collection/${destination}/` : '/'});
            }
          }
          onCopy(name, redirect, destination);
        },
        showError
      );
    };

    componentDidUpdate() {
      if (this.state.redirect) {
        this.setState({redirect: null});
      }
    }

    render() {
      const {entity, collection, onCancel} = this.props;
      const {redirect} = this.state;

      if (!entity) {
        return null;
      }

      if (redirect) {
        return <Redirect to={redirect} />;
      }

      return (
        <CopyModal
          onClose={onCancel}
          onConfirm={this.copy}
          entity={entity}
          collection={collection || null}
          jumpToEntity={collection || entity.entityType !== 'collection'}
        />
      );
    }
  }
);
