/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {elementInstancesTreeStore} from 'App/ProcessInstance/ElementInstanceLog/ElementInstancesTree/elementInstancesTreeStore';
import {modificationsStore} from 'modules/stores/modifications';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processInstanceMigrationMappingStore} from 'modules/stores/processInstanceMigrationMapping';
import {batchModificationStore} from 'modules/stores/batchModification';
import {panelStatesStore} from 'modules/stores/panelStates';
import {diagramOverlaysStore} from 'modules/stores/diagramOverlays';
import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';
import {authenticationStore} from 'modules/stores/authentication';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';
import {elementTimeStampStore} from 'modules/stores/elementTimeStamp';
import {executionCountToggleStore} from 'modules/stores/executionCountToggle';
import {dateRangePopoverStore} from 'modules/stores/dateRangePopover';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {decisionDefinitionStore} from 'modules/stores/decisionDefinition';
import {currentTheme} from 'modules/stores/currentTheme';
import {notificationsStore} from 'modules/stores/notifications';

/**
 * This utility exists only for testing purposes.
 */
function resetAllStores() {
  elementInstancesTreeStore.reset();
  modificationsStore.reset();
  instanceHistoryModificationStore.reset();
  processInstanceMigrationStore.reset();
  processInstanceMigrationMappingStore.reset();
  batchModificationStore.reset();
  panelStatesStore.reset();
  diagramOverlaysStore.reset();
  processInstancesSelectionStore.reset();
  authenticationStore.reset();
  incidentsPanelStore.clearSelection();
  elementTimeStampStore.reset();
  executionCountToggleStore.reset();
  dateRangePopoverStore.reset();
  variableFilterStore.reset();
  decisionDefinitionStore.reset();
  currentTheme.reset();
  notificationsStore.reset();
}

export {resetAllStores};
