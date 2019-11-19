/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ButtonGroup, Button, Modal, Form} from 'components';

import {withErrorHandling} from 'HOC';
import {t} from 'translation';
import {getDefinitionsWithTenants, getTenantsWithDefinitions} from './service';
import TenantSource from './TenantSource';
import DefinitionSource from './DefinitionSource';
import MultiDefinitionSource from './MultiDefinitionSource';
import {showError} from 'notifications';

export default withErrorHandling(
  class AddSourceModal extends React.Component {
    state = {
      addBy: 'definition',
      sources: [],
      tenants: null,
      definitions: null,
      valid: false
    };

    async componentDidMount() {
      this.loadDefinitionsWithTenants();
      if (this.props.tenantsAvailable) {
        this.loadTenantsWithDefinitions();
      }
    }

    componentDidUpdate(prevProps) {
      if (!prevProps.tenantsAvailable && this.props.tenantsAvailable) {
        this.loadTenantsWithDefinitions();
      }
    }

    loadDefinitionsWithTenants = () =>
      this.props.mightFail(
        getDefinitionsWithTenants(),
        definitions => this.setState({definitions}),
        showError
      );

    loadTenantsWithDefinitions = () =>
      this.props.mightFail(
        getTenantsWithDefinitions(),
        tenants => this.setState({tenants}),
        showError
      );

    onClose = () => {
      this.props.onClose();
      this.reset();
    };

    reset = () => this.setState({addBy: 'definition', sources: [], valid: false});

    onConfirm = () => {
      if (this.state.valid) {
        this.props.onConfirm(this.state.sources);
      }
    };

    changeAddBy = addBy => this.setState({addBy, valid: false});
    onChange = sources => this.setState({sources, valid: true});
    setInvalid = () => this.setState({valid: false});

    render() {
      const {addBy, definitions, tenants, valid} = this.state;
      const {tenantsAvailable} = this.props;

      return (
        <Modal
          className="AddSourceModal"
          open={this.props.open}
          onClose={this.onClose}
          onConfirm={this.onConfirm}
        >
          <Modal.Header>{t('home.sources.add')}</Modal.Header>
          <Modal.Content>
            {tenantsAvailable && (
              <ButtonGroup>
                <Button
                  active={addBy === 'definition'}
                  onClick={() => this.changeAddBy('definition')}
                >
                  {t('home.sources.definition.label')}
                </Button>
                <Button active={addBy === 'tenant'} onClick={() => this.changeAddBy('tenant')}>
                  {t('common.tenant.label')}
                </Button>
              </ButtonGroup>
            )}
            <Form>
              {addBy === 'definition' && tenantsAvailable && (
                <DefinitionSource
                  definitionsWithTenants={definitions}
                  onChange={this.onChange}
                  setInvalid={this.setInvalid}
                />
              )}

              {addBy === 'tenant' && tenantsAvailable && (
                <TenantSource
                  tenantsWithDefinitions={tenants}
                  onChange={this.onChange}
                  setInvalid={this.setInvalid}
                  preSelectTenant={!tenantsAvailable}
                />
              )}

              {!tenantsAvailable && (
                <MultiDefinitionSource
                  definitions={definitions}
                  onChange={this.onChange}
                  setInvalid={this.setInvalid}
                />
              )}
            </Form>
          </Modal.Content>
          <Modal.Actions>
            <Button className="cancel" onClick={this.onClose}>
              {t('common.cancel')}
            </Button>
            <Button
              disabled={!valid}
              variant="primary"
              color="blue"
              className="confirm"
              onClick={this.onConfirm}
            >
              {t('common.add')}
            </Button>
          </Modal.Actions>
        </Modal>
      );
    }
  }
);
