package io.zeebe.hashindex.benchmarks;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class Benchmarks
{
    public static void main(String... args) throws Exception
    {

        final Options opts = new OptionsBuilder()
                .include(".*")
                .warmupIterations(5)
                .measurementIterations(10)
                .jvmArgs("-server")
                .forks(1)
                .build();

        new Runner(opts).run();

    }
}
