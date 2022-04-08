/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';

import {Button, Icon, Input, Modal, Table} from 'components';
import {loadVariables} from 'services';
import {t} from 'translation';
import {withErrorHandling} from 'HOC';
import {showError} from 'notifications';

import {updateVariables} from './service';

import './RenameVariablesModal.scss';

export function RenameVariablesModal({
  onClose,
  onChange,
  mightFail,
  definitionKey,
  availableTenants,
}) {
  const [variables, setVariables] = useState();
  const [query, setQuery] = useState('');
  const [renamedVariables, setRenamedVariables] = useState(new Map());

  useEffect(() => {
    mightFail(
      loadVariables([
        {
          processDefinitionKey: definitionKey,
          processDefinitionVersions: ['all'],
          tenantIds: availableTenants,
        },
      ]),
      (variables) => {
        setVariables(variables);
        setRenamedVariables(
          new Map(
            variables
              .filter((variable) => variable.label)
              .map((variable) => [
                variable,
                {
                  variableName: variable.name,
                  variableType: variable.type,
                  variableLabel: variable.label,
                },
              ])
          )
        );
      },
      showError
    );
  }, [availableTenants, definitionKey, mightFail]);

  function updateVariableNames() {
    mightFail(
      updateVariables(definitionKey, Array.from(renamedVariables.values())),
      () => {
        onChange();
        onClose();
      },
      showError
    );
  }

  const filteredVariables =
    variables?.filter(({name}) => name.toLowerCase().includes(query.toLowerCase())) || [];

  return (
    <Modal
      open
      size="max"
      onClose={onClose}
      onConfirm={updateVariableNames}
      className="RenameVariablesModal"
    >
      <Modal.Header>{t('report.definition.variables.rename')}</Modal.Header>
      <Modal.Content>
        <div className="header">
          <div className="info">
            {t('report.definition.variables.renameInfo')}
            <p>
              <b>{t('report.definition.variables.renameWarning')}</b>
            </p>
          </div>
          <div className="searchInputContainer">
            <Input
              value={query}
              className="searchInput"
              placeholder={t('report.groupBy.searchForVariable')}
              type="text"
              onChange={(evt) => {
                setQuery(evt.target.value);
              }}
              onClear={() => {
                setQuery('');
              }}
            />
            <Icon className="searchIcon" type="search" size="20" />
          </div>
        </div>
        <Table
          head={[
            {label: t('report.definition.variables.variableName'), id: 'name'},
            {label: t('report.definition.variables.type'), id: 'type'},
            {label: t('report.definition.variables.newName'), id: 'newName'},
          ]}
          body={filteredVariables.map((variable) => [
            variable.name,
            variable.type,
            <Input
              className="nameInput"
              type="text"
              value={renamedVariables.get(variable)?.variableLabel || ''}
              onChange={(evt) =>
                setRenamedVariables(
                  (renamedVariables) =>
                    new Map(
                      renamedVariables.set(variable, {
                        variableName: variable.name,
                        variableType: variable.type,
                        variableLabel: evt.target.value,
                      })
                    )
                )
              }
            />,
          ])}
          loading={!variables}
          noData={
            (variables?.length > 0 &&
              filteredVariables.length === 0 &&
              t('events.table.noResults')) ||
            undefined
          }
        />
      </Modal.Content>
      <Modal.Actions>
        <Button main onClick={onClose}>
          {t('common.close')}
        </Button>
        <Button main primary onClick={updateVariableNames}>
          {t('common.update')}
        </Button>
      </Modal.Actions>
    </Modal>
  );
}

export default withErrorHandling(RenameVariablesModal);
