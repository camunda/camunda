package org.camunda.tngp.broker.it.workflow;

import org.camunda.tngp.client.cmd.BrokerRequestException;
import org.camunda.tngp.protocol.clientapi.ErrorCode;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class BrokerRequestExceptionMatcher extends BaseMatcher<BrokerRequestException>
{

    protected ErrorCode expectedDetailCode;

    public static BrokerRequestExceptionMatcher brokerException(ErrorCode expectedDetailCode)
    {
        final BrokerRequestExceptionMatcher matcher = new BrokerRequestExceptionMatcher();
        matcher.expectedDetailCode = expectedDetailCode;
        return matcher;
    }

    @Override
    public boolean matches(Object item)
    {
        if (item == null || !(item instanceof BrokerRequestException))
        {
            return false;
        }

        final BrokerRequestException exception = (BrokerRequestException) item;

        return expectedDetailCode == exception.getErrorCode();
    }

    @Override
    public void describeTo(Description description)
    {
        description.appendText(BrokerRequestException.class.getSimpleName());
        description.appendText(" with detail code " + expectedDetailCode);

    }

}
