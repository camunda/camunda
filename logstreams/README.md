# Zeebe Logstreams

Library for fault tolerant stream processing on top of append-only logs.

**Features**

* Append-only storage abstraction layer
* Logical positions
* Streaming log iterators
* Random access based on logical positions
* Asynchronous I/O
* Cache-efficient memory access patterns
* Garbage-free in the data path (exception: allocating new journal segments)

## Logstreams & Stream Processors

Logstreams combine the properties of logs and streams.

As a **log**, it is an ordered sequence of entries, each entry having a unique, monotonically increasing position. A consumer can read the log sequentially, or it can "go back in time" or replay.

As a **stream**, entries can be consumed as they are appended to the log.

The library offers a means to implement stateful **stream processors** on top of the logstreams. Stream processors consume one logstream and may (asynchronously) write to another logstream. Between consumed entries they may maintain state. The state is managed and can be snapshotted periodically. Snapshots are stamped with the position of the logstream entry which was consumed before the snapshot was taken. In case of a failure, the last snapshot is restored and the log is replayed.

## Storage

This library offers a storage abstraction over the default file system journal. Entries are written at a specific **address** into storage. In the file system journal, the address points to a segment file and a (byte) offset within that file. Layers building on top of the storage abstraction don't deal with the physical addresses but with **logical positions**. Translation between addresses and positions is done with the **block index** data structure, mapping ranges of positions to addressable blocks in which the entries are located.

## Performance

The storage is **optimized for streaming (sequential) read access**: sequential access is 100x up to 1000x faster than random access due to batching effects (buffering) while reading and predictable memory access facilitating pre-fetching.

**Writes are asynchronous** facilitate batching effects for maximum throughput. The [zb-dispatcher] is used for buffering writes for the log appender agent.

## Usage

### Creating a Log Stream

```java
final LogStream stream = LogStreams.createLogStream()
        .withLogName("log")
        .withPartitionId(1)
        .withMaxFragmentSize(1024 * 1024 * 16)
        .withActorScheduler(scheduler)
        .withLogStorage(logStorage)
        .build();

stream.open();

// ... use log stream

stream.close();
```

