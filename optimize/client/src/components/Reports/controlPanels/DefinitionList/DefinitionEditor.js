/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState, useMemo} from 'react';
import {TextInput, Button, Stack, Form} from '@carbon/react';
import {Edit, Maximize} from '@carbon/icons-react';

import {BPMNDiagram, VersionPopover, TenantPopover, TenantInfo} from 'components';
import {withErrorHandling} from 'HOC';
import {loadProcessDefinitionXml} from 'services';
import {t} from 'translation';
import {showError} from 'notifications';
import {getOptimizeProfile} from 'config';

import {loadTenants, loadVersions} from './service';
import RenameVariablesModal from './RenameVariablesModal';
import DiagramModal from './DiagramModal';

import './DefinitionEditor.scss';

export function DefinitionEditor({
  mightFail,
  collection,
  type,
  definition,
  tenantInfo,
  onChange,
  filters,
}) {
  const {key, versions, tenantIds} = definition;
  const firstVersion = versions[0];
  const firstTenant = tenantIds[0];

  const [availableVersions, setAvailableVersions] = useState([]);
  const [selectedSpecificVersions, setSelectedSpecificVersions] = useState(
    isSpecificVersion(versions) ? versions : []
  );
  const [availableTenants, setAvailableTenants] = useState(versions.length ? tenantInfo : []);
  const [xml, setXml] = useState();
  const [loadingXml, setLoadingXml] = useState(true);
  const [loadingVersions, setLoadingVersions] = useState(false);
  const [loadingTenants, setLoadingTenants] = useState(false);
  const [displayName, setDisplayName] = useState(definition.displayName);
  const [diagramModalOpen, setDiagramModalOpen] = useState(false);
  const [variableModalOpen, setVariableModalOpen] = useState(false);
  const [optimizeProfile, setOptimizeProfile] = useState();
  const tenantInfoIds = useMemo(() => tenantInfo?.map(({id}) => id), [tenantInfo]);

  useEffect(() => {
    mightFail(loadVersions(type, collection, key), setAvailableVersions, showError);
  }, [mightFail, collection, key, type]);

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
    })();
  }, []);

  useEffect(() => {
    setLoadingXml(true);
    mightFail(
      loadProcessDefinitionXml(key, firstVersion, firstTenant),
      (xml) => {
        setXml(xml);
      },
      showError,
      () => setLoadingXml(false)
    );
  }, [mightFail, key, firstVersion, firstTenant]);

  const handleModalClose = (evt) => {
    evt.stopPropagation();
    setDiagramModalOpen(false);
  };

  const showOnlyTenant = availableTenants?.length === 1 && optimizeProfile === 'ccsm';

  return (
    <>
      <Stack gap={6} className="DefinitionEditor">
        <div className="title">
          <div className="cds--label">{t('report.definition.' + type)}</div>
          <div className="definitionName">{definition.name}</div>
        </div>
        <Form className="selectionPanel">
          <div className="version entry">
            <VersionPopover
              label={t('common.definitionSelection.version.label')}
              versions={availableVersions}
              selected={definition.versions}
              selectedSpecificVersions={selectedSpecificVersions}
              loading={loadingVersions}
              align="bottom-left"
              onChange={async (newVersions) => {
                setLoadingVersions(true);
                if (isSpecificVersion(newVersions)) {
                  setSelectedSpecificVersions(newVersions);
                }

                if (!newVersions.length) {
                  setAvailableTenants([]);
                  await onChange({...definition, versions: newVersions, tenantIds: []});
                  setLoadingVersions(false);
                } else {
                  mightFail(
                    loadTenants(type, [{key: definition.key, version: newVersions}], collection),
                    async ([{tenants: newAvailableTenants}]) => {
                      const prevTenants = availableTenants;
                      const deselectedTenants = prevTenants
                        ?.map(({id}) => id)
                        .filter((tenant) => !definition.tenantIds?.includes(tenant));
                      const tenantIds = newAvailableTenants
                        ?.map(({id}) => id)
                        .filter((tenant) => !deselectedTenants?.includes(tenant));

                      setAvailableTenants(newAvailableTenants);
                      await onChange({...definition, versions: newVersions, tenantIds});
                    },
                    showError,
                    () => setLoadingVersions(false)
                  );
                }
              }}
            />
          </div>
          {availableTenants?.length > 1 && (
            <div className="tenant entry">
              <TenantPopover
                tenants={availableTenants}
                selected={definition.tenantIds}
                loading={loadingTenants}
                onChange={async (newTenants) => {
                  setLoadingTenants(true);
                  await onChange({...definition, tenantIds: newTenants});
                  setLoadingTenants(false);
                }}
                align="bottom-left"
                label={t('common.tenant.label')}
              />
            </div>
          )}
          {showOnlyTenant && <TenantInfo tenant={availableTenants[0]} />}
          <TextInput
            id="ProcessRenameInput"
            size="sm"
            placeholder={t('report.displayNamePlaceholder')}
            value={displayName}
            onChange={(evt) => setDisplayName(evt.target.value)}
            labelText={t('report.displayName')}
            onBlur={(evt) => {
              // This input field is inside a Popover. When the user click outside of the Popover, we want to close it.
              // Popovers close, when they receive a click event outside of the Popover. However, click events are
              // only triggered after the mouseup, while the blur event fires after mousedown. The onChange handler
              // we call here causes a react state update that rerenders the ReportRenderer. So if a user clicks on
              // the ReportRenderer to close the popover, we get a blur event that causes the original target of the click to
              // disappear because of the rerender, so that the click event is never generated and the Popover stays open.
              //
              // To fix that, we need to identify that the blur event was caused by a click rather than a key event (1) and
              // then delay the execution of the onChange update until after the click has been processed (2). (1) can be
              // handled with the relatedTarget property of the event. For keyboard blur events this is always set to the new
              // element to receive focus. Some mouse events can also set this, if the clicked element is focusable. But as the
              // ReportRenderer does not have any focusable elements, we can use this to identify a click in the ReportRenderer
              // For (2), we register a one time mouseup event listener on the body. As the click handler fires after mouseup,
              // we further delay the execution of onChange using setTimeout to give the Popover time to process the click event.
              if (!evt.relatedTarget) {
                document.body.addEventListener(
                  'mouseup',
                  () => setTimeout(() => onChange({...definition, displayName})),
                  {once: true}
                );
              } else {
                onChange({...definition, displayName});
              }
            }}
          />
        </Form>
        <div className="diagram">
          <BPMNDiagram xml={xml} disableNavigation loading={loadingXml} />
          <Button
            kind="tertiary"
            disabled={loadingXml || !xml}
            onClick={() => setDiagramModalOpen(true)}
            renderIcon={Maximize}
            size="sm"
            className="diagramExpandBtn"
          >
            {t('common.entity.viewModel.model')}
          </Button>
        </div>
        <Button
          kind="tertiary"
          size="sm"
          onClick={() => {
            setVariableModalOpen(true);
          }}
          renderIcon={Edit}
        >
          {t('report.definition.variables.rename')}
        </Button>
      </Stack>
      <RenameVariablesModal
        filters={filters}
        open={variableModalOpen}
        definitionKey={definition.key}
        availableTenants={tenantInfoIds}
        onChange={() => onChange(definition)}
        onClose={() => {
          setVariableModalOpen(false);
        }}
      />
      <DiagramModal
        open={diagramModalOpen}
        onClose={handleModalClose}
        xml={xml}
        definitionName={definition.name}
      />
    </>
  );
}

function isSpecificVersion(versions) {
  return versions && versions[0] !== 'latest' && versions[0] !== 'all';
}

export default withErrorHandling(DefinitionEditor);
