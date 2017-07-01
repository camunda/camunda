package io.zeebe.perftest.reporter;

@FunctionalInterface
public interface RateReportFn
{

    void reportRate(long timestamp, long intervalValue);

}
