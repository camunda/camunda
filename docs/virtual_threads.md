# Guidance: Using Virtual Threads at Camunda

Virtual threads, finalized in Java 21 ([JEP 444](https://openjdk.org/jeps/444)), are lightweight threads managed by the Java runtime rather than the operating system.
They reduce the effort of writing, maintaining, and observing high-throughput concurrent applications, especially those using the thread-per-request style.
However, they are not a silver bullet and should not be used indiscriminately.
This guide provides practical advice and examples for when and how to use virtual threads in our codebase.

## When to Use Virtual Threads

- **Thread-per-request style:** Virtual threads enable server applications to handle each request in its own thread.
- **High concurrency, I/O-bound workloads:** Use virtual threads when you need to handle many concurrent, mostly-blocking tasks (e.g., network or file I/O).
- **Simplifying blocking code:** Virtual threads allow you to write straightforward, blocking code instead of complex asynchronous pipelines.

**Do not** use virtual threads for:
- CPU-bound tasks (virtual threads do not make computation faster).
- Long-running or infinite loops.

## Best Practices

- **Do not pool virtual threads.** Create a new virtual thread for each task.
- **Keep virtual thread lifetimes short.** They are cheap to create, but should not be held indefinitely.
- **Avoid thread-local state for resource pooling.** Use thread locals only when necessary, and never to pool expensive resources.
- **Be aware of pinning:** Use of `synchronized` or native methods can pin a virtual thread to its carrier, reducing scalability. Prefer `ReentrantLock` for long-running or I/O-guarded critical sections.

## Practical Example: FileSetManager

In the `FileSetManager` from the S3 backup store, we perform blocking I/O operations such as downloading and copying files. These are good candidates for virtual threads because:

- The operations are I/O-bound and may block for significant periods.
- Using virtual threads allows us to write straightforward, blocking code without complex asynchronous constructs.

### Applied Pattern

- Use a `Semaphore` to limit the number of concurrent operations (e.g., uploads/downloads).
- For each file operation, start a new virtual thread that acquires the semaphore, performs the blocking I/O, and then releases the semaphore.
- This allows you to write simple, blocking code while still controlling concurrency and avoiding resource exhaustion.

### Example: Limiting IO concurrency with Virtual Threads

Below is a practical example from the S3 FileSetManager, showing how to use virtual threads and a semaphore to limit concurrent S3 uploads.
This pattern is especially useful for I/O-heavy workloads where you want to avoid the complexity of asynchronous code, but still need to limit the number of concurrent operations to avoid exhausting resources (such as S3 connections).

```java
package io.camunda.zeebe.backup.s3;
import java.util.concurrent.Semaphore;

class FileSetManager {
  private final Semaphore concurrencyLimit = new Semaphore(10);
  private final Thread.Builder threadBuilder = Thread.ofVirtual().name("zeebe-backup-", 0);

  void save(final String prefix, final NamedFileSet files) {
    for (final var namedFile : files.namedFiles().values()) {
      final var fileName = namedFile.fileName();
      final var filePath = namedFile.filePath();

      // Start a virtual thread for each file operation
      threadBuilder.start(() -> {
        try {
          // Block until a permit is available
          concurrencyLimit.acquire();
          client.putObject(
              put -> put.bucket(config.bucketName()).key(prefix + fileName),
              AsyncRequestBody.fromFile(filePath))
            // Block until the operation completes
            .join();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          LOG.error("Thread interrupted while saving file {}", fileName, e);
        } catch (Exception e) {
          LOG.error("Failed to save file {}", fileName, e);
        } finally {
          concurrencyLimit.release();
        }
      });
    }
  }
}
```

**Why this works well:**
- Each file operation runs in its own virtual thread, so blocking code is simple and readable.
- The semaphore ensures you never exceed the allowed number of concurrent S3 operations.
- No thread pooling or complex future composition is needed.

---

This pattern is especially useful for I/O-heavy workloads where you want to avoid the complexity of asynchronous code, but still need to limit the number of concurrent operations to avoid exhausting resources (such as S3 connections).

## Observability and Debugging

- Virtual threads are visible to Java debuggers and profilers.
- Use `jcmd <pid> Thread.dump_to_file -format=json <file>` for thread dumps that include virtual threads.
- JFR emits events for virtual thread start, end, pinning, and failures.
- The system property `jdk.tracePinnedThreads` can help diagnose pinning issues.

## Further Reading

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)

