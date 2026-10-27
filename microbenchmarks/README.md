# Microbenchmarks

This module contains JMH (Java Microbenchmark Harness) benchmarks, allowing us to run microbenchmarks against our implementations. To qualify and compare performance improvements.

## Quick Start

### 1. Build the Benchmark JAR

From the root directory:

```bash
./mvnw clean package -pl microbenchmarks -DskipTests
```

**Note:** Don't use `-Dquickly` as it skips the shade plugin which is required to build the benchmark JAR.

### 2. Run the Benchmarks

**Option A: Use the helper script**

```bash
cd microbenchmarks
./run-benchmark.sh
```

**Option B: Run directly**

```bash
cd microbenchmarks
java -jar target/benchmarks.jar MsgpackBenchmark
```

## Running Specific Benchmarks

```bash
# Run only serialization
java -jar target/benchmarks.jar MsgpackBenchmark.serialize

# Run only deserialization
java -jar target/benchmarks.jar MsgpackBenchmark.deserialize

# List all available benchmarks
java -jar target/benchmarks.jar -l

# Get help
java -jar target/benchmarks.jar -h
```

## Customizing Benchmark Execution

### Change Number of Iterations

```bash
java -jar target/benchmarks.jar MsgpackBenchmark \
  -wi 10 \    # 10 warmup iterations (default: 5)
  -i 20 \     # 20 measurement iterations (default: 10)
  -f 3        # 3 forks (default: 2)
```

### Change Time Unit

```bash
java -jar target/benchmarks.jar MsgpackBenchmark \
  -tu ns      # nanoseconds
  # Other options: us (microseconds), ms (milliseconds), s (seconds)
```

### Change Benchmark Mode

```bash
java -jar target/benchmarks.jar MsgpackBenchmark \
  -bm avgt    # Average time per operation
  # Other options: thrpt (throughput), sample, ss (single shot)
```

### Export Results

**JSON format:**

```bash
java -jar target/benchmarks.jar MsgpackBenchmark \
  -rf json \
  -rff results.json
```

**CSV format:**

```bash
java -jar target/benchmarks.jar MsgpackBenchmark \
  -rf csv \
  -rff results.csv
```

**Text format:**

```bash
java -jar target/benchmarks.jar MsgpackBenchmark \
  -rf text \
  -rff results.txt
```

### Run with Profilers

**GC Profiler (garbage collection stats):**

```bash
java -jar target/benchmarks.jar MsgpackBenchmark -prof gc
```

**Stack Profiler (hotspot analysis):**

```bash
java -jar target/benchmarks.jar MsgpackBenchmark -prof stack
```

**JFR Profiler (Java Flight Recorder):**

```bash
java -jar target/benchmarks.jar MsgpackBenchmark -prof jfr
```

**Async Profiler (requires setup):**

```bash
java -jar target/benchmarks.jar MsgpackBenchmark -prof async:libPath=/path/to/libasyncProfiler.so
```

## Understanding Results

Example output:

```
Benchmark                                                          (batchSize)  Mode  Cnt     Score     Error   Units
MsgpackBenchmark.deserializeWithConstructor                               1000  avgt   10     0.420 ±   0.020   us/op
MsgpackBenchmark.deserializeWithConstructor:gc.alloc.rate                 1000  avgt   10  4215.834 ± 262.180  MB/sec
MsgpackBenchmark.deserializeWithConstructor:gc.alloc.rate.norm            1000  avgt   10  1855.200 ±  31.873    B/op
MsgpackBenchmark.deserializeWithConstructor:gc.count                      1000  avgt   10   689.000            counts
MsgpackBenchmark.deserializeWithConstructor:gc.time                       1000  avgt   10   839.000                ms
MsgpackBenchmark.deserializeWithConstructor                              10000  avgt   10     0.421 ±   0.018   us/op
MsgpackBenchmark.deserializeWithConstructor:gc.alloc.rate                10000  avgt   10  4203.568 ± 218.667  MB/sec
MsgpackBenchmark.deserializeWithConstructor:gc.alloc.rate.norm           10000  avgt   10  1855.200 ±  31.873    B/op
MsgpackBenchmark.deserializeWithConstructor:gc.count                     10000  avgt   10   687.000            counts
MsgpackBenchmark.deserializeWithConstructor:gc.time                      10000  avgt   10   760.000                ms
MsgpackBenchmark.deserializeWithConstructor                             100000  avgt   10     0.444 ±   0.022   us/op
MsgpackBenchmark.deserializeWithConstructor:gc.alloc.rate               100000  avgt   10  4029.973 ± 196.691  MB/sec
MsgpackBenchmark.deserializeWithConstructor:gc.alloc.rate.norm          100000  avgt   10  1875.200 ±   0.001    B/op
MsgpackBenchmark.deserializeWithConstructor:gc.count                    100000  avgt   10   784.000            counts
MsgpackBenchmark.deserializeWithConstructor:gc.time                     100000  avgt   10   774.000                ms
MsgpackBenchmark.deserializeWithoutConstructor                            1000  avgt   10     0.254 ±   0.015   us/op
MsgpackBenchmark.deserializeWithoutConstructor:gc.alloc.rate              1000  avgt   10   282.676 ±  16.488  MB/sec
MsgpackBenchmark.deserializeWithoutConstructor:gc.alloc.rate.norm         1000  avgt   10    75.200 ±   0.001    B/op
MsgpackBenchmark.deserializeWithoutConstructor:gc.count                   1000  avgt   10    46.000            counts
MsgpackBenchmark.deserializeWithoutConstructor:gc.time                    1000  avgt   10    71.000                ms
MsgpackBenchmark.deserializeWithoutConstructor                           10000  avgt   10     0.303 ±   0.009   us/op
MsgpackBenchmark.deserializeWithoutConstructor:gc.alloc.rate             10000  avgt   10   236.916 ±   7.078  MB/sec
MsgpackBenchmark.deserializeWithoutConstructor:gc.alloc.rate.norm        10000  avgt   10    75.200 ±   0.001    B/op
MsgpackBenchmark.deserializeWithoutConstructor:gc.count                  10000  avgt   10    38.000            counts
MsgpackBenchmark.deserializeWithoutConstructor:gc.time                   10000  avgt   10    81.000                ms
MsgpackBenchmark.deserializeWithoutConstructor                          100000  avgt   10     0.338 ±   0.036   us/op
MsgpackBenchmark.deserializeWithoutConstructor:gc.alloc.rate            100000  avgt   10   213.409 ±  22.660  MB/sec
MsgpackBenchmark.deserializeWithoutConstructor:gc.alloc.rate.norm       100000  avgt   10    75.200 ±   0.001    B/op
MsgpackBenchmark.deserializeWithoutConstructor:gc.count                 100000  avgt   10    41.000            counts
MsgpackBenchmark.deserializeWithoutConstructor:gc.time                  100000  avgt   10   293.000                ms
MsgpackBenchmark.serialize                                                1000  avgt   10     0.136 ±   0.015   us/op
MsgpackBenchmark.serialize:gc.alloc.rate                                  1000  avgt   10     0.001 ±   0.001  MB/sec
MsgpackBenchmark.serialize:gc.alloc.rate.norm                             1000  avgt   10    ≈ 10⁻⁴              B/op
MsgpackBenchmark.serialize:gc.count                                       1000  avgt   10       ≈ 0            counts
MsgpackBenchmark.serialize                                               10000  avgt   10     0.354 ±   0.016   us/op
MsgpackBenchmark.serialize:gc.alloc.rate                                 10000  avgt   10     0.001 ±   0.001  MB/sec
MsgpackBenchmark.serialize:gc.alloc.rate.norm                            10000  avgt   10    ≈ 10⁻⁴              B/op
MsgpackBenchmark.serialize:gc.count                                      10000  avgt   10       ≈ 0            counts
MsgpackBenchmark.serialize                                              100000  avgt   10     0.270 ±   0.018   us/op
MsgpackBenchmark.serialize:gc.alloc.rate                                100000  avgt   10     0.001 ±   0.001  MB/sec
MsgpackBenchmark.serialize:gc.alloc.rate.norm                           100000  avgt   10    ≈ 10⁻⁴              B/op
MsgpackBenchmark.serialize:gc.count                                     100000  avgt   10     1.000            counts
MsgpackBenchmark.serialize:gc.time                                      100000  avgt   10    14.000                ms

```

- **Mode**: `avgt` = average time per iteration
- **Cnt**: Number of measurement iterations (20 = 2 forks × 10 iterations)
- **Score**: Average throughput (higher is better)
- **Error**: 99.9% confidence interval
- **Units**: `us/op` = microseconds per operation

## Tips for Accurate Results

1. **Close unnecessary applications** to reduce system noise
2. **Disable CPU frequency scaling** if possible:

   ```bash
   # Linux example (requires root)
   sudo cpupower frequency-set --governor performance
   ```
3. **Run multiple forks** to account for JVM variance (already configured)
4. **Let the JVM warm up** adequately (already configured)
5. **Run on a quiet system** with minimal background activity
6. **Use fixed heap size** (already configured)
7. **Disable turbo boost** for more consistent results

## Advanced Examples

### Complete Performance Analysis

```bash
java -jar target/benchmarks.jar MsgpackBenchmark \
  -prof gc \
  -prof stack \
  -rf json \
  -rff full-results.json \
  -v EXTRA
```

### Quick Test Run (fewer iterations)

```bash
java -jar target/benchmarks.jar MsgpackBenchmark \
  -wi 2 \
  -i 3 \
  -f 1
```

### Compare Different Scenarios

Run benchmarks with different JVM options (example with `MsgpackBenchmark`):

```bash
# With G1GC (default)
java -jar target/benchmarks.jar MsgpackBenchmark -rf json -rff g1-results.json

# With ZGC
java -XX:+UseZGC -jar target/benchmarks.jar MsgpackBenchmark -rf json -rff zgc-results.json

# With Parallel GC
java -XX:+UseParallelGC -jar target/benchmarks.jar MsgpackBenchmark -rf json -rff parallel-results.json
```

## Troubleshooting

### Build fails

```bash
# Make sure you're building from the root directory
./mvnw clean package -pl microbenchmarks -DskipTests
```

### OutOfMemoryError

```bash
# Increase heap size
java -Xms2G -Xmx2G -jar target/benchmarks.jar MsgpackBenchmark
```

### No benchmark JAR found

The JAR should be at `microbenchmarks/target/benchmarks.jar`. If it's missing, rebuild:

```bash
./mvnw clean package -pl microbenchmarks -DskipTests
```

## References

- [JMH Documentation](https://github.com/openjdk/jmh)
- [JMH Samples](https://hg.openjdk.java.net/code-tools/jmh/file/tip/jmh-samples/src/main/java/org/openjdk/jmh/samples/)
- [Avoiding Benchmarking Pitfalls](https://shipilev.net/blog/2014/nanotrusting-nanotime/)

