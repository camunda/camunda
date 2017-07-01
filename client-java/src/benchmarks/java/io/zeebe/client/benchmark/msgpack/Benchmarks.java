package io.zeebe.client.benchmark.msgpack;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class Benchmarks
{


    public static void main(String[] args) throws RunnerException
    {
        final Options opt = new OptionsBuilder()
                .include(".*" + POJOSerializationBenchmark.class.getSimpleName() + ".*")
                .include(".*" + POJODeserializationBenchmark.class.getSimpleName() + ".*")
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
