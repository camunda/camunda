/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useEffect, ChangeEvent} from 'react';
import classnames from 'classnames';
import {
  Button,
  ComboBox,
  TableSelectAll,
  TableSelectRow,
  TableToolbar,
  TableToolbarContent,
  TableToolbarSearch,
} from '@carbon/react';

import {Modal, Table, TenantPopover} from 'components';
import {formatters} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';
import {areTenantsAvailable, getOptimizeProfile} from 'config';
import {useErrorHandling} from 'hooks';
import {Source} from 'types';

import {
  DefinitionWithTenants,
  TenantWithDefinitions,
  getDefinitionsWithTenants,
  getTenantsWithDefinitions,
} from './service';

import './SourcesModal.scss';

const {formatTenantName} = formatters;

interface SourcesModalProps {
  onClose: () => void;
  onConfirm: (definitions?: Source[]) => void;
  confirmText: string;
  preSelectAll?: boolean;
}

export default function SourcesModal({
  onClose,
  onConfirm,
  confirmText,
  preSelectAll,
}: SourcesModalProps): JSX.Element {
  const [definitions, setDefinitions] = useState<DefinitionWithTenants[]>();
  const [tenants, setTenants] = useState<TenantWithDefinitions[]>([]);
  const [selected, setSelected] = useState<Source[]>([]);
  const [selectedTenant, setSelectedTenant] = useState<string | null | undefined>();
  const [query, setQuery] = useState('');
  const [optimizeProfile, setOptimizeProfile] = useState('');
  const {mightFail} = useErrorHandling();

  useEffect(() => {
    mightFail(
      getDefinitionsWithTenants(),
      (definitions) => {
        if (preSelectAll) {
          setSelected(definitions.map(format));
        }
        setDefinitions(definitions);
      },
      showError
    );
  }, [mightFail, preSelectAll]);

  useEffect(() => {
    (async () => {
      const tenantAvailable = await areTenantsAvailable();
      if (tenantAvailable) {
        mightFail(getTenantsWithDefinitions(), setTenants, showError);
        setOptimizeProfile(await getOptimizeProfile());
      }
    })();
  }, [mightFail]);

  const removeExtraTenants = (def: Source): Source => {
    if (typeof selectedTenant === 'undefined') {
      return def;
    }

    return {...def, tenants: [selectedTenant]};
  };

  function createCollection() {
    onConfirm(selected);
  }

  const filteredDefinitions =
    definitions?.filter(
      (def) =>
        definitionHasSelectedTenant(def, selectedTenant) &&
        (def.name || def.key).toLowerCase().includes(query.toLowerCase())
    ) || [];

  function deselectAll() {
    setSelected(
      selected.concat(
        filteredDefinitions
          .filter(({key}) => !selected.some(({definitionKey}) => key === definitionKey))
          .map(format)
          .map(removeExtraTenants)
      )
    );
  }

  function selectAll() {
    setSelected(
      selected.filter(
        ({definitionKey}) => !filteredDefinitions.some(({key}) => definitionKey === key)
      )
    );
  }

  const isInSelected = ({key}: {key?: string}) =>
    selected.some(({definitionKey}) => key === definitionKey);
  const allChecked = filteredDefinitions.every(isInSelected);
  const allIndeterminate = !allChecked && filteredDefinitions.some(isInSelected);

  const tableHead = [
    {
      label: (
        <TableSelectAll
          id="checked"
          key="checked"
          name="checked"
          aria-label="checked"
          className={classnames({hidden: !filteredDefinitions.length})}
          indeterminate={allIndeterminate}
          checked={allChecked}
          onSelect={({target}) => {
            const {checked} = target as HTMLInputElement;
            if (checked) {
              deselectAll();
            } else {
              selectAll();
            }
          }}
        />
      ),
      id: 'checked',
      sortable: false,
      width: 30,
    },
    {label: t('common.name'), id: 'name', sortable: true},
    {label: t('common.entity.type'), id: 'type', sortable: false, width: 80},
  ];

  if (tenants.length !== 0) {
    tableHead.push({
      label: t('common.tenant.label-plural'),
      id: 'tenants',
      sortable: false,
      width: 80,
    });
  }

  const tenantsSelectorItems = [
    {value: undefined, label: t('common.collection.modal.allTenants')},
    ...tenants.map((tenant) => ({
      value: tenant.id,
      label: formatTenantName(tenant),
    })),
  ];

  return (
    <Modal open onClose={onClose} size="lg" className="SourcesModal" isOverflowVisible>
      <Modal.Header title={t('home.sources.add')} />
      <Modal.Content>
        <Table
          toolbar={
            <TableToolbar>
              <TableToolbarContent>
                {tenants.length !== 0 && (
                  <ComboBox
                    id="tenantsSelector"
                    className="tenantsSelector"
                    placeholder={t('common.select').toString()}
                    items={tenantsSelectorItems}
                    itemToString={(tenant) =>
                      (tenant as (typeof tenantsSelectorItems)[number])?.label.toString()
                    }
                    titleText={t('common.tenant.label-plural')}
                    initialSelectedItem={tenantsSelectorItems[0]}
                    onChange={({selectedItem}) => {
                      setSelected([]);
                      setSelectedTenant(selectedItem?.value);
                    }}
                  />
                )}
                <TableToolbarSearch
                  placeholder={t('home.search.name').toString()}
                  onChange={(evt) => {
                    setQuery((evt as ChangeEvent<HTMLInputElement>).target.value);
                  }}
                  onClear={() => {
                    setQuery('');
                  }}
                  expanded
                  data-modal-primary-focus
                />
              </TableToolbarContent>
            </TableToolbar>
          }
          head={tableHead}
          body={filteredDefinitions.map((def) => {
            const selectedDefinition = selected.find(
              ({definitionKey}) => def.key === definitionKey
            );

            const key = def.name || def.key;
            const body = [
              <TableSelectRow
                checked={!!selectedDefinition}
                id={def.key}
                name={key}
                aria-label={key}
                onSelect={({target}) => {
                  const {checked} = target as HTMLInputElement;
                  if (checked) {
                    setSelected([...selected, removeExtraTenants(format(def))]);
                  } else {
                    setSelected((selected) =>
                      selected.filter(({definitionKey}) => def.key !== definitionKey)
                    );
                  }
                }}
              />,
              key,
              def.type || '',
            ];

            if (tenants.length !== 0) {
              if (optimizeProfile === 'ccsm' && tenants.length === 1) {
                const {id, name} = def?.tenants?.[0] || {};
                body.push(<>{formatTenantName({id, name})}</>);
              } else {
                body.push(
                  // clicking inside the popover
                  <TenantPopover
                    tenants={def.tenants}
                    selected={selectedDefinition?.tenants || ['']}
                    disabled={!selectedDefinition}
                    onChange={(newTenants) => {
                      setSelected(
                        selected.map((selectedDefinition) => {
                          if (def.key === selectedDefinition.definitionKey) {
                            return {
                              ...selectedDefinition,
                              tenants:
                                newTenants.length === 0 ? [def.tenants[0]?.id || null] : newTenants,
                            };
                          }
                          return selectedDefinition;
                        })
                      );
                    }}
                    floating
                  />
                );
              }
            }

            return body;
          })}
          disablePagination
          noHighlight
          loading={!definitions}
          allowLocalSorting
        />
      </Modal.Content>
      <Modal.Footer>
        <Button kind="secondary" className="cancel" onClick={onClose}>
          {t('common.cancel')}
        </Button>
        <Button className="confirm" onClick={createCollection}>
          {confirmText}
        </Button>
      </Modal.Footer>
    </Modal>
  );
}

function format({key, type, tenants}: DefinitionWithTenants): Source {
  return {
    definitionKey: key,
    definitionType: type,
    tenants: tenants.map(({id}) => id),
  };
}

function definitionHasSelectedTenant(def: DefinitionWithTenants, selectedTenant?: string | null) {
  return def.tenants.some(({id}) =>
    typeof selectedTenant !== 'undefined' ? selectedTenant === id : true
  );
}
