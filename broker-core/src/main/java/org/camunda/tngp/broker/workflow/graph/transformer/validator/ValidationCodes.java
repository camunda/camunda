package org.camunda.tngp.broker.workflow.graph.transformer.validator;

public class ValidationCodes
{
    public static final int NO_EXECUTABLE_PROCESS = 1;
    public static final int MORE_THAN_ONE_EXECUTABLE_PROCESS = 2;

    public static final int NO_PROCESS_ID = 10;
    public static final int PROCESS_ID_TOO_LONG = 11;

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
