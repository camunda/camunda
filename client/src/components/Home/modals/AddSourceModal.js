/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {ButtonGroup, Button, Modal, Form} from 'components';

import {withErrorHandling} from 'HOC';
import {t} from 'translation';
import DefinitionSource from './DefinitionSource';
import {getDefinitionsWithTenants, getTenantsWithDefinitions} from './service';
import TenantSource from './TenantSource';
import {showError} from 'notifications';

export default withErrorHandling(
  class AddSourceModal extends React.Component {
    state = {
      addBy: 'definition',
      sources: [],
      tenantsWithDefinitions: null,
      definitionsWithTenants: null,
      valid: false
    };

    componentDidMount() {
      this.loadDefinitionsWithTenants();
      this.loadTenantsWithDefinitions();
    }

    loadDefinitionsWithTenants = () =>
      this.props.mightFail(
        getDefinitionsWithTenants(),
        definitionsWithTenants => this.setState({definitionsWithTenants}),
        showError
      );

    loadTenantsWithDefinitions = () =>
      this.props.mightFail(
        getTenantsWithDefinitions(),
        tenantsWithDefinitions => this.setState({tenantsWithDefinitions}),
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
      const {addBy, definitionsWithTenants, tenantsWithDefinitions, valid} = this.state;

      return (
        <Modal
          className="AddSourceModal"
          open={this.props.open}
          onClose={this.onClose}
          onConfirm={this.onConfirm}
        >
          <Modal.Header>{t('home.sources.add')}</Modal.Header>
          <Modal.Content>
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
            <Form>
              {addBy === 'definition' && (
                <DefinitionSource
                  definitionsWithTenants={definitionsWithTenants}
                  onChange={this.onChange}
                  setInvalid={this.setInvalid}
                />
              )}

              {addBy === 'tenant' && (
                <TenantSource
                  tenantsWithDefinitions={tenantsWithDefinitions}
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
