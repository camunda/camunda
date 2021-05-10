/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useState, useEffect} from 'react';
import {withRouter} from 'react-router-dom';

import {Button, Icon, Modal, Checklist} from 'components';
import {withErrorHandling} from 'HOC';
import {getCollection} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';

import {loadDefinitions, loadTenants} from './service';

import './AddDefinition.scss';

export function AddDefinition({mightFail, location, definitions, type, onAdd}) {
  const [availableDefinitions, setAvailableDefinitions] = useState([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedDefinitions, setSelectedDefinitions] = useState([]);

  const collection = getCollection(location.pathname);

  useEffect(() => {
    mightFail(
      loadDefinitions(type, collection),
      (definitions) => setAvailableDefinitions(definitions),
      showError
    );
  }, [mightFail, collection, type]);

  function isNameUnique(name) {
    return availableDefinitions.filter((definition) => definition.name === name).length === 1;
  }

  return (
    <Button
      small
      className="AddDefinition"
      onClick={(evt) => {
        evt.stopPropagation();
        setSelectedDefinitions([]);
        setModalOpen(true);
      }}
    >
      <Icon type="plus" />
      {t('common.add')}
      <Modal open={modalOpen} onClose={() => setModalOpen(false)} className="AddDefinition__Modal">
        <Modal.Header>
          {t('report.definition.add', {type: t('report.definition.' + type)})}
        </Modal.Header>
        <Modal.Content>
          <span className="title">{t(`report.definition.${type}-plural`)}</span>
          <Checklist
            allItems={availableDefinitions}
            selectedItems={selectedDefinitions}
            onChange={setSelectedDefinitions}
            formatter={() =>
              availableDefinitions.map(({key, name}) => {
                const hasDefinition = (definition) => definition.key === key;
                return {
                  id: key,
                  label: isNameUnique(name) ? name : `${name} (${key})`,
                  checked:
                    selectedDefinitions.some(hasDefinition) || definitions.some(hasDefinition),
                  disabled: definitions.some(hasDefinition),
                };
              })
            }
            labels={{
              search: t('report.definition.search', {type: t(`common.${type}.label`)}),
              empty: t('common.definitionSelection.noDefinition'),
            }}
          />
        </Modal.Content>
        <Modal.Actions>
          <Button main onClick={() => setModalOpen(false)}>
            {t('common.cancel')}
          </Button>
          <Button
            primary
            main
            onClick={() => {
              setModalOpen(false);
              mightFail(
                loadTenants(
                  type,
                  selectedDefinitions.map(({key}) => ({
                    key,
                    versions: ['latest'],
                  })),
                  collection
                ),
                (tenantInfo) =>
                  onAdd(
                    selectedDefinitions.map(({key, name}, idx) => ({
                      key,
                      name,
                      displayName: name,
                      versions: ['latest'],
                      tenantIds: tenantInfo[idx].tenants.map(({id}) => id),
                    }))
                  ),
                showError
              );
            }}
            disabled={selectedDefinitions.length === 0}
          >
            {t('common.add')}
          </Button>
        </Modal.Actions>
      </Modal>
    </Button>
  );
}

export default withRouter(withErrorHandling(AddDefinition));
