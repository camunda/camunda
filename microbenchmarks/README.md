# Msgpack Benchmarks

This module contains JMH (Java Microbenchmark Harness) benchmarks for testing the performance of msgpack serialization and deserialization.

## Benchmarks

### MsgpackBenchmark

Tests the performance of:
- **serialize**: Serializing a Pojo object to msgpack format
- **deserialize**: Deserializing msgpack data back into a Pojo object

The benchmarks use a representative Pojo with various field types:
- Enum values
- Long and integer primitives
- String values
- Packed data
- Binary data
- Nested objects

## Quick Start

### 1. Build the Benchmark JAR

```bash
./mvnw clean package -pl microbenchmarks -DskipTests
```

Or from the root directory:
```bash
cd /home/carlosana/workspace/camunda/camunda
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
Benchmark                           Mode  Cnt    Score    Error   Units
MsgpackBenchmark.serialize         thrpt   20  1234.567 ± 12.345  ops/us
MsgpackBenchmark.deserialize       thrpt   20   987.654 ±  9.876  ops/us
```

- **Mode**: `thrpt` = throughput (operations per time unit)
- **Cnt**: Number of measurement iterations (20 = 2 forks × 10 iterations)
- **Score**: Average throughput (higher is better)
- **Error**: 99.9% confidence interval
- **Units**: `ops/us` = operations per microsecond

## Benchmark Configuration

The benchmarks are configured with the following settings (in `MsgpackBenchmark.java`):

```java
@BenchmarkMode(Mode.Throughput)              // Measure operations per time unit
@OutputTimeUnit(TimeUnit.MICROSECONDS)       // Report in microseconds
@Warmup(iterations = 5, time = 1, ...)       // 5 warmup iterations of 1 second each
@Measurement(iterations = 10, time = 1, ...) // 10 measurement iterations
@Fork(value = 2, ...)                        // Run in 2 separate JVMs
```

JVM arguments:
- `-Xms1G -Xmx1G`: Fixed heap size to reduce GC variance

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

Run benchmarks with different JVM options:
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
cd /home/carlosana/workspace/camunda/camunda
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

