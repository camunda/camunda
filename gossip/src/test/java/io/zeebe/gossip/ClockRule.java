package io.zeebe.gossip;

import java.time.Duration;

import io.zeebe.util.time.ClockUtil;
import org.junit.rules.ExternalResource;

public final class ClockRule extends ExternalResource
{
    private final boolean pinCurrentTime;

    private ClockRule(boolean pinCurrentTime)
    {
        this.pinCurrentTime = pinCurrentTime;
    }

    public static ClockRule pinCurrentTime()
    {
        return new ClockRule(true);
    }

    @Override
    protected void before() throws Throwable
    {
        if (pinCurrentTime)
        {
            ClockUtil.pinCurrentTime();
        }
    }

    @Override
    protected void after()
    {
        ClockUtil.reset();
    };

    public void addTime(Duration durationToAdd)
    {
        ClockUtil.addTime(durationToAdd);
    }

}
