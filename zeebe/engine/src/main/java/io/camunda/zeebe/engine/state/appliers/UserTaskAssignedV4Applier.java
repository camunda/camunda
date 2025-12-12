/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.globallistener.MutableGlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.engine.state.mutable.MutableUserTaskState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public final class UserTaskAssignedV4Applier
    implements TypedEventApplier<UserTaskIntent, UserTaskRecord> {

  private final MutableUserTaskState userTaskState;
  private final MutableElementInstanceState elementInstanceState;
  private final MutableUsageMetricState usageMetricState;
  private final MutableGlobalListenersState globalListenersState;

  public UserTaskAssignedV4Applier(final MutableProcessingState processingState) {
    userTaskState = processingState.getUserTaskState();
    elementInstanceState = processingState.getElementInstanceState();
    usageMetricState = processingState.getUsageMetricState();
    globalListenersState = processingState.getGlobalListenersState();
  }

  @Override
  public void applyState(final long key, final UserTaskRecord value) {
    // Unpin global listeners configuration from the user task
    // Note that the pinning information is stored in the intermediate
    // state, which needs to be accessed before it is deleted
    final UserTaskRecord intermediateStateRecord =
        userTaskState.getIntermediateState(key).getRecord();
    if (intermediateStateRecord != null) {
      unpinGlobalListenersConfig(intermediateStateRecord);
    }

    final var userTaskRecord = new UserTaskRecord();
    userTaskRecord.wrapWithoutVariables(value);
    userTaskState.update(userTaskRecord.setChangedAttributes(List.of()).setAction(""));
    userTaskState.updateUserTaskLifecycleState(key, LifecycleState.CREATED);

    // Clear operational data related to the current assign(claim) transition
    userTaskState.deleteIntermediateState(key);
    userTaskState.deleteInitialAssignee(key);

    final var elementInstance = elementInstanceState.getInstance(value.getElementInstanceKey());
    if (elementInstance != null) {
      final long scopeKey = elementInstance.getValue().getFlowScopeKey();
      final var scopeInstance = elementInstanceState.getInstance(scopeKey);
      if (scopeInstance != null && scopeInstance.isActive()) {
        elementInstance.resetTaskListenerIndex(ZeebeTaskListenerEventType.assigning);
        elementInstanceState.updateInstance(elementInstance);
      }
    }

    if (StringUtils.isNotEmpty(value.getAssignee())) {
      usageMetricState.recordTUMetric(value.getTenantId(), value.getAssignee());
    }
  }

  public void unpinGlobalListenersConfig(final UserTaskRecord userTaskRecord) {
    final long pinnedConfigKey = userTaskRecord.getListenersConfigKey();
    // Only unpin if there is a pinned config
    if (pinnedConfigKey < 0) {
      return;
    }

    // Remove pinned entry
    globalListenersState.unpinConfiguration(pinnedConfigKey, userTaskRecord.getUserTaskKey());

    // If no other user task references this config, remove the versioned config
    if (!globalListenersState.isConfigurationVersionPinned(pinnedConfigKey)) {
      globalListenersState.deleteConfigurationVersion(pinnedConfigKey);
    }
  }
}
