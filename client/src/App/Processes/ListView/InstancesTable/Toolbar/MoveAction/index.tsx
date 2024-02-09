/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {useLocation} from 'react-router-dom';
import {TableBatchAction} from '@carbon/react';
import {Move} from '@carbon/react/icons';
import {Restricted} from 'modules/components/Restricted';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {isWithinMultiInstance} from 'modules/bpmn-js/utils/isWithinMultiInstance';
import {isAttachedToAnEventBasedGateway} from 'modules/bpmn-js/utils/isAttachedToAnEventBasedGateway';
import isNil from 'lodash/isNil';

const MoveAction: React.FC = observer(() => {
  const location = useLocation();
  const {process, tenant, flowNodeId} = getProcessInstanceFilters(
    location.search,
  );

  const {hasSelectedRunningInstances} = processInstancesSelectionStore;

  const businessObject: BusinessObject | null = flowNodeId
    ? processXmlStore.state.diagramModel?.elementsById[flowNodeId]
    : null;

  const isTypeSupported = (businessObject: BusinessObject) => {
    return (
      businessObject.$type !== 'bpmn:StartEvent' &&
      businessObject.$type !== 'bpmn:BoundaryEvent' &&
      !isMultiInstance(businessObject)
    );
  };

  const isDisabled =
    isNil(businessObject) ||
    flowNodeId === undefined ||
    !isTypeSupported(businessObject) ||
    !hasSelectedRunningInstances ||
    isWithinMultiInstance(businessObject) ||
    isAttachedToAnEventBasedGateway(businessObject);

  const getTooltipText = () => {
    if (!isDisabled) {
      return undefined;
    }

    if (flowNodeId === undefined || isNil(businessObject)) {
      return 'Please select an element from the diagram first.';
    }
    if (!isTypeSupported(businessObject)) {
      return 'The selected element type is not supported.';
    }
    if (!hasSelectedRunningInstances) {
      return 'You can only move flow node instances in active or incident state.';
    }
    if (isWithinMultiInstance(businessObject)) {
      return 'Elements inside a multi instance element are not supported.';
    }
    if (isAttachedToAnEventBasedGateway(businessObject)) {
      return 'Elements attached to an event based gateway are not supported.';
    }
  };

  return (
    <Restricted
      scopes={['write']}
      resourceBasedRestrictions={{
        scopes: ['UPDATE_PROCESS_INSTANCE'],
        permissions: processesStore.getPermissions(process, tenant),
      }}
    >
      <TableBatchAction
        renderIcon={Move}
        onClick={() => {}}
        disabled={isDisabled}
        title={getTooltipText()}
      >
        Move
      </TableBatchAction>
    </Restricted>
  );
});

export {MoveAction};
