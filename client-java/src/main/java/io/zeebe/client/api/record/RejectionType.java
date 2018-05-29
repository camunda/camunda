package io.zeebe.client.api.record;

public enum RejectionType
{

    /**
     * A value of the command is not valid.
     */
    BAD_VALUE,

    /**
     * The command is not applicable to the current state of the addressed state machine.
     * Example: A COMPLETE command is not applicable when the addressed job is already
     * in state COMPLETED.
     */
    NOT_APPLICABLE,

    /**
     * The broker could not process the command for internal reasons. The rejection message
     * provides details.
     */
    PROCESSING_ERROR,

}
