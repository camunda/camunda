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

    public static final int NO_OUTGOING_SEQUENCE_FLOW = 30;
    public static final int MORE_THAN_ONE_OUTGOING_SEQUENCE_FLOW = 31;
    public static final int OUTGOING_SEQUENCE_FLOW_AT_END_EVENT = 32;

}
