/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.response;

import io.camunda.client.api.response.ActivateAdHocSubProcessActivitiesResponse;
import io.camunda.client.api.response.AssignUserTaskResponse;
import io.camunda.client.api.response.CancelBatchOperationResponse;
import io.camunda.client.api.response.CancelProcessInstanceResponse;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.CompleteUserTaskResponse;
import io.camunda.client.api.response.DeleteResourceResponse;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.response.MigrateProcessInstanceResponse;
import io.camunda.client.api.response.ModifyProcessInstanceResponse;
import io.camunda.client.api.response.ResetClockResponse;
import io.camunda.client.api.response.ResolveIncidentResponse;
import io.camunda.client.api.response.ResumeBatchOperationResponse;
import io.camunda.client.api.response.SuspendBatchOperationResponse;
import io.camunda.client.api.response.ThrowErrorResponse;

public class EmptyApiResponse
    implements CompleteUserTaskResponse,
        CancelProcessInstanceResponse,
        ActivateAdHocSubProcessActivitiesResponse,
        AssignUserTaskResponse,
        CancelBatchOperationResponse,
        CompleteJobResponse,
        DeleteResourceResponse,
        FailJobResponse,
        MigrateProcessInstanceResponse,
        ModifyProcessInstanceResponse,
        ResetClockResponse,
        ResolveIncidentResponse,
        ResumeBatchOperationResponse,
        SuspendBatchOperationResponse,
        ThrowErrorResponse {}
