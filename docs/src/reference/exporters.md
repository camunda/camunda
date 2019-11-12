# Exporters

Regardless of how an exporter is loaded (whether through an external JAR or not),
all exporters interact in the same way with the broker, which is defined by the
[Exporter interface](https://github.com/zeebe-io/zeebe/tree/{{commit}}/exporter-api/src/main/java/io/zeebe/exporter/api/Exporter.java).

## Loading

Once configured, exporters are loaded as part of the broker startup phase, before
any processing is done.

During the loading phase, the configuration for each exporter is validated, such that
the broker will not start if:

* An exporter ID is not unique
* An exporter points to a non-existent/non-accessible JAR
* An exporter points to a non-existent/non-instantiable class
* An exporter instance throws an exception in its `Exporter#configure` method.

The last point is there to provide individual exporters to perform lightweight
validation of their configuration (e.g. fail if missing arguments).

One of the caveat of the last point is that an instance of an exporter is created and
immediately thrown away; therefore, exporters should not perform any computationally
heavy work during instantiation/configuration.

> **Note:** Zeebe will create an isolated class loader for every JAR referenced by
> exporter configurations - that is, only once per JAR; if the same JAR is reused to
> define different exporters, then these will share the same class loader.
>
> This has some nice properties, primarily that different exporters can depend on
> the same third party libraries without having to worry about versions, or class
> name collisions.
>
> Additionally, exporters use the system class loader for system classes, or
> classes packaged as part of the Zeebe JAR.

Exporter specific configuration is handled through the exporter's `[exporters.args]`
nested map. This provides a simple `Map<String, Object>` which is passed directly
in form of a [Configuration](https://github.com/zeebe-io/zeebe/tree/{{commit}}/exporter-api/src/main/java/io/zeebe/exporter/api/context/Configuration.java)
object when the broker calls the `Exporter#configure(Configuration)` method.

Configuration occurs at two different phases: during the broker startup phase, and
once every time a leader is elected for a partition.

## Processing

At any given point, there is exactly one leader
node for a given partition. Whenever a node becomes the leader for a partition, one
of the things it will do is run an instance of an
[exporter stream processor](https://github.com/zeebe-io/zeebe/tree/{{commit}}/broker/src/main/java/io/zeebe/broker/exporter/stream/ExporterDirector.java).

This stream processor will create exactly one instance of each configured exporter,
and forward every record written on the stream to each of these in turn.

> **Note:** this implies that there will be exactly one instance of every exporter for
> every partition: if you have 4 partitions, and at least 4 threads for processing,
> then there are potentially 4 instances of your exporter exporting simultaneously.

Note that Zeebe only guarantees at-least-once semantics, that is, a record will be
seen at least once by an exporter, and maybe more. Cases where this may happen
include:

* During reprocessing after raft failover (i.e. new leader election)
* On error if the position has not been updated yet

To reduce the amount of duplicate records an exporter will process, the stream
processor will keep track of the position of the last successfully exported record
for every single exporter; the position is sufficient since a stream is an ordered
sequence of records whose position is monotonically increasing. This position is
set by the exporter itself once it can guarantee a record has been successfully
updated.

> **Note:** although Zeebe tries to reduce the amount of duplicate records an
> exporter has to handle, it is likely that it will have to; therefore, it is
> necessary that export operations be idempotent.
>
> This can be implemented either in the exporter itself, but if it exports to an
> external system, it is recommended that you perform deduplication there to reduce
> the load on Zeebe itself. Refer to the exporter specific documentation for how
> this is meant to be achieved.

### Error handling

If an error occurs during the `Exporter#open(Context)` phase, the stream
processor will fail and be restarted, potentially fixing the error; worst case
scenario, this means no exporter is running at all until these errors stop.

If an error occurs during the `Exporter#close` phase, it will be logged, but will
still allow other exporters to gracefully finish their work.

If an error occurs during processing, we will retry infinitely the same record until
no error is produced. Worst case scenario, this means a failing exporter could bring
all exporters to a halt. Currently, exporter implementations are expected to
implement their own retry/error handling strategies, though this may change in the
future.

### Performance impact

Zeebe naturally incurs a performance impact for each loaded exporter. A slow
exporter will slow down all other exporters for a given partition, and, in the
worst case, could completely block a thread.

It's therefore recommended to keep exporters as simple as possible, and perform
any data enrichment or transformation through the external system.
