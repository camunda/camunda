/**
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
package io.zeebe.broker.workflow.graph.transformer.validator;

public class ValidationCodes
{
    public static final int NO_EXECUTABLE_PROCESS = 1;
    public static final int MORE_THAN_ONE_EXECUTABLE_PROCESS = 2;

    public static final int MISSING_ID = 10;
    public static final int ID_TOO_LONG = 11;

    public static final int NO_START_EVENT = 20;
    public static final int MORE_THAN_ONE_NONE_START_EVENT = 21;
    public static final int NOT_SUPPORTED_START_EVENT = 22;

    public static final int MORE_THAN_ONE_OUTGOING_SEQUENCE_FLOW = 30;
    public static final int OUTGOING_SEQUENCE_FLOW_AT_END_EVENT = 31;

    public static final int NOT_SUPPORTED_TASK_TYPE = 40;

    public static final int NO_TASK_DEFINITION = 50;
    public static final int NO_TASK_TYPE = 51;
    public static final int INVALID_TASK_RETRIES = 52;
    public static final int NO_TASK_HEADER_KEY = 53;
    public static final int NO_TASK_HEADER_VALUE = 54;

    public static final int INVALID_JSON_PATH_EXPRESSION = 60;
    public static final int PROHIBITED_JSON_PATH_EXPRESSION = 61;
    public static final int REDUNDANT_MAPPING = 62;

}
