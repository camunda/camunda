/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';

import {Button, Modal, Form, LoadingIndicator} from 'components';
import {t} from 'translation';
import {getDefinitionTenants} from './service';
import {withErrorHandling} from 'HOC';
import Checklist from './Checklist';

export default withErrorHandling(
  class EditSourcesModal extends Component {
    constructor(props) {
      super(props);

      this.state = {
        selectedTenants: this.props.source.tenants,
        definitionTenants: null
      };
    }

    async componentDidMount() {
      const {definitionKey, definitionType} = this.props.source;
      this.props.mightFail(getDefinitionTenants(definitionKey, definitionType), ({tenants}) => {
        this.setState({
          definitionTenants: tenants
        });
      });
    }

    onConfirm = () => {
      if (this.state.selectedTenants.length > 0) {
        this.props.onConfirm(this.state.selectedTenants.map(({id}) => id));
      }
    };

    updateSelectedTenants = (id, checked) => {
      let newTenants;
      if (checked) {
        const tenantsToSelect = this.state.definitionTenants.find(tenant => tenant.id === id);
        newTenants = [...this.state.selectedTenants, tenantsToSelect];
      } else {
        newTenants = this.state.selectedTenants.filter(tenant => tenant.id !== id);
      }
      this.setState({selectedTenants: newTenants});
    };

    renderTenantsList = () => {
      const {selectedTenants, definitionTenants} = this.state;

      if (!definitionTenants) {
        return <LoadingIndicator />;
      }

      return (
        <Checklist
          data={definitionTenants}
          onChange={this.updateSelectedTenants}
          selectAll={() => this.setState({selectedTenants: definitionTenants})}
          deselectAll={() => this.setState({selectedTenants: []})}
          formatter={({id, name}) => ({
            id,
            label: name,
            checked: selectedTenants.some(tenant => tenant.id === id)
          })}
        />
      );
    };

    render() {
      const {
        onClose,
        source: {definitionName, definitionKey}
      } = this.props;

      return (
        <Modal className="EditSourceModal" open onClose={onClose} onConfirm={this.onConfirm}>
          <Modal.Header>
            {t('common.editName', {name: definitionName || definitionKey})}
          </Modal.Header>
          <Modal.Content>
            <Form>
              {t('common.tenant.label-plural')}
              <Form.Group>{this.renderTenantsList()}</Form.Group>
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
              disabled={!this.state.selectedTenants.length}
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
