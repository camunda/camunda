# Exporters

As Zeebe processes jobs and workflows, or performs internal maintenance (e.g.
raft failover), it will generate an ordered stream of records:

![record-stream](/basics/exporters-stream.png)

While the clients provide no way to inspect this stream directly, Zeebe can load
and configure user code that can process each and every one of those records, in the form of an exporter.

An **exporter** provides a single entry point to process every record that is
written on a stream.

With it, you can:

* Persist historical data by pushing it to an external data warehouse
* Export records to a visualization tool (e.g. [zeebe-simple-monitor](https://github.com/zeebe-io/zeebe-simple-monitor/))

Zeebe will only load exporters which are configured through the main Zeebe YAML
configuration file.

Once an exporter is configured, the next time Zeebe is started, the exporter
will start receiving records. Note that it is only guaranteed to see records
produced from that point on.

For more information, you can read the [reference information page](/reference/exporters.html),
and you can find a reference implementation in the form of the Zeebe-maintained
[ElasticSearch exporter](https://github.com/zeebe-io/zeebe/tree/{{commit}}/exporters/elasticsearch-exporter).

## Considerations

The main impact exporters have on a Zeebe cluster is that they remove the burden
of persisting data indefinitely.

Once data is not needed by Zeebe itself anymore, it will query its exporters to
know if it can be safely deleted, and if so, will permanently erase it, thereby
reducing disk usage.

> **Note:**, if no exporters are configured at all, then Zeebe will automatically
> erase data when it is not necessary anymore. If you need historical data,
> then you **need** to configure an exporter to stream records into your external
> data warehouse.
