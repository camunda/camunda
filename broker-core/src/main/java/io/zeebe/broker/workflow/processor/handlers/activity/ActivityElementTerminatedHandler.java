/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.processor.handlers.activity;

import io.zeebe.broker.workflow.model.element.ExecutableActivity;
import io.zeebe.broker.workflow.processor.BpmnStepContext;
import io.zeebe.broker.workflow.processor.handlers.IncidentResolver;
import io.zeebe.broker.workflow.processor.handlers.element.ElementTerminatedHandler;
import io.zeebe.broker.workflow.state.ElementInstance;

/**
 * Performs usual ElementTerminated logic and publishes any deferred record. At the moment, it will
 * always try to publish deferred tokens, even if there are none; this could be optimized by
 * checking {@link ElementInstance#getNumberOfActiveExecutionPaths()}
 *
 * @param <T>
 */
public class ActivityElementTerminatedHandler<T extends ExecutableActivity>
    extends ElementTerminatedHandler<T> {

  public ActivityElementTerminatedHandler(IncidentResolver incidentResolver) {
    super(incidentResolver);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    publishDeferredRecords(context);
    return super.handleState(context);
  }
}
