/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {withErrorHandling} from 'HOC';
import {ConfirmationModal} from 'components';
import {showError} from 'notifications';
import {deleteEntity} from 'services';

export default withErrorHandling(
  class Deleter extends React.Component {
    static defaultProps = {
      deleteEntity: ({entityType, id}) => deleteEntity(entityType, id),
      getName: ({name}) => name
    };

    state = {
      conflictedItems: [],
      loading: false
    };

    componentDidUpdate(prevProps) {
      const {entity, checkConflicts} = this.props;
      if (prevProps.entity !== entity && entity) {
        if (checkConflicts) {
          this.props.mightFail(
            checkConflicts(entity),
            ({conflictedItems}) => {
              this.setState({conflictedItems, loading: false});
            },
            error => {
              showError(error);
              this.setState({conflictedItems: [], loading: false});
            }
          );
        } else {
          this.setState({conflictedItems: [], loading: false});
        }
      }
    }

    delete = () => {
      const {entity, onDelete, onClose, deleteEntity} = this.props;

      this.setState({loading: true});
      this.props.mightFail(
        deleteEntity(entity),
        (...args) => {
          onDelete(...args);
          onClose();
        },
        error => {
          showError(error);
          this.setState({loading: false});
        }
      );
    };

    render() {
      const {entity, onClose, getName} = this.props;
      const {conflictedItems, loading} = this.state;

      if (!entity) {
        return null;
      }

      return (
        <ConfirmationModal
          onClose={onClose}
          onConfirm={this.delete}
          entityName={getName(entity)}
          conflict={{type: 'delete', items: conflictedItems}}
          loading={loading}
        />
      );
    }
  }
);
