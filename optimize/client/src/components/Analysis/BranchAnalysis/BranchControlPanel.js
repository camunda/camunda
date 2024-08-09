/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DefinitionSelection, SelectionPreview} from 'components';
import {t} from 'translation';

import {ControlPanel} from '../ControlPanel';

import './BranchControlPanel.scss';

export default function BranchControlPanel(props) {
  const {
    processDefinitionKey,
    processDefinitionVersions,
    tenantIds,
    xml,
    onChange,
    updateHover,
    updateSelection,
    hoveredControl,
    hoveredNode,
    filters,
  } = props;

  function hover(element) {
    return function () {
      updateHover(element);
    };
  }

  function isProcDefSelected() {
    return processDefinitionKey && processDefinitionVersions;
  }

  function renderInput({type, bpmnKey}) {
    const disableFlowNodeSelection = !isProcDefSelected();

    return (
      <div className="config" name={type} onMouseOver={hover(type)} onMouseOut={hover(null)}>
        <SelectionPreview
          disabled={disableFlowNodeSelection}
          onClick={() => updateSelection(type, null)}
          highlighted={
            !disableFlowNodeSelection &&
            (hoveredControl === type || (hoveredNode && hoveredNode.$instanceOf(bpmnKey)))
          }
        >
          {props[type]
            ? props[type].name || props[type].id
            : t(`analysis.emptySelectionLabel.${type}`)}
        </SelectionPreview>
      </div>
    );
  }

  return (
    <ControlPanel
      processDefinitionKey={processDefinitionKey}
      processDefinitionVersions={processDefinitionVersions}
      tenantIds={tenantIds}
      onChange={onChange}
      filters={filters}
    >
      {t('analysis.selectLabel')}
      <DefinitionSelection
        type="process"
        definitionKey={processDefinitionKey}
        versions={processDefinitionVersions}
        tenants={tenantIds}
        xml={xml}
        onChange={({key, versions, tenantIds, identifier}) =>
          onChange({
            processDefinitionKey: key,
            processDefinitionVersions: versions,
            tenantIds,
            identifier,
          })
        }
      />
      {t('analysis.gatewayLabel')}
      {renderInput({type: 'gateway', bpmnKey: 'bpmn:Gateway'})}
      {t('analysis.endEventLabel')}
      {renderInput({type: 'endEvent', bpmnKey: 'bpmn:EndEvent'})}
    </ControlPanel>
  );
}
