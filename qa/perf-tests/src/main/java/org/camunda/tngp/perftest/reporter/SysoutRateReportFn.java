package org.camunda.tngp.perftest.reporter;

public class SysoutRateReportFn implements RateReportFn
{

    @Override
    public void reportRate(long timestamp, long intervalEndNs)
    {
        System.out.printf("\t%d\t%d\n", timestamp, intervalEndNs);
    }

}
