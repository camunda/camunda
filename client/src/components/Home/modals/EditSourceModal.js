/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Button, Modal, Form} from 'components';
import {t} from 'translation';
import {getDefinitionTenants, formatTenants} from './service';
import {withErrorHandling} from 'HOC';
import ItemsList from './ItemsList';

export default withErrorHandling(
  class EditSourceModal extends React.Component {
    constructor(props) {
      super(props);

      this.state = {
        selectedTenants: this.props.source.tenants,
        definitionTenants: null
      };
    }

    componentDidMount() {
      const {definitionKey, definitionType} = this.props.source;
      this.props.mightFail(getDefinitionTenants(definitionKey, definitionType), ({tenants}) => {
        this.setState({
          definitionTenants: tenants
        });
      });
    }

    onConfirm = () => {
      const {selectedTenants} = this.state;
      if (selectedTenants.length > 0) {
        this.props.onConfirm(selectedTenants.map(({id}) => id));
      }
    };

    render() {
      const {
        onClose,
        source: {definitionName, definitionKey}
      } = this.props;

      const {selectedTenants, definitionTenants} = this.state;

      return (
        <Modal className="EditSourceModal" open onClose={onClose} onConfirm={this.onConfirm}>
          <Modal.Header>
            {t('common.editName', {name: definitionName || definitionKey})}
          </Modal.Header>
          <Modal.Content>
            <Form>
              {t('common.tenant.label-plural')}
              <Form.Group>
                <ItemsList
                  selectedItems={selectedTenants}
                  allItems={definitionTenants}
                  onChange={selectedTenants => this.setState({selectedTenants})}
                  formatter={formatTenants}
                />
              </Form.Group>
            </Form>
          </Modal.Content>
          <Modal.Actions>
            <Button className="cancel" onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="primary"
              color="blue"
              className="confirm"
              disabled={!selectedTenants.length}
              onClick={this.onConfirm}
            >
              {t('common.apply')}
            </Button>
          </Modal.Actions>
        </Modal>
      );
    }
  }
);
