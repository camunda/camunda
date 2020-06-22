# Backpressure

When a broker receives a client request, it is written to the *event stream* first (see section [Internal Processing](/basics/internal-processing.html) for details), and processed later by the stream processor.
If the processing is slow or if there are many client requests in the stream, it might take too long for the processor to start processing the command.
If the broker keeps accepting new requests from the client, the back log increases and the processing latency can grow beyond an acceptable time.
To avoid such problems, Zeebe employs a backpressure mechanism.
When the broker receives more requests than it can process with an acceptable latency, it rejects some requests (see section [Error handling](/reference/grpc.html)).

### Terminologies
* *RTT* - The time between the request is accepted by the broker and the response to the request is sent back to the gateway.
* *inflight count* - The number of requests accepted by the broker but the response is not yet sent.
* *limit* - maximum number of flight requests. When the inflight count is above the limit, any new incoming request will be rejected.

Note that the limit and inflight count are calculated per partition.

### Backpressure algorithms

Zeebe uses adaptive algorithms from [concurrency-limits](https://github.com/Netflix/concurrency-limits) to dynamically calculate the limit.
Zeebe can be configured with one of the following backpressure algorithms.

#### Fixed Limit
With “fixed limit” one can configure a fixed value of the limit.
Zeebe operators are recommended to evaluate the latencies observed with different values for limit.
Note that with different cluster configurations, you may have to choose different limit values.

#### AIMD
AIMD calculates the limit based on the configured *requestTimeout*.
When the RTT for a request *requestTimeout*, the limit is increased by 1.
When the RTT is longer than *requestTimeout*,
the limit will be reduced according to the configured *backoffRatio*.

#### Vegas
Vegas is an adaptive limit algorithm based on TCP Vegas congestion control algorithm.
Vegas estimates a base latency as the minimum observed latency.
This base RTT is the expected latency when there is no load.
Whenever the RTT deviates from the base RTT, a new limit is calculated based on the vegas algorithm.
Vegas allows to configure two parameters - *alpha* and *beta*.
The values correspond to a queue size that is estimated by the Vegas algorithm based on the observed RTT, base RTT, and current limit.
When the queue size is below *alpha*, the limit is increased.
When the queue size is above *beta*, the limit is decreased.

### Gradient
Gradient is an adaptive limit algorithm that dynamically calculates the limit based on observed RTT.
In the gradient algorithm, the limit is adjusted based on the gradient of observed RTT and an observed minimum RTT.
If gradient is less than 1, the limit is decreased otherwise the limit is increased.

### Gradient2
Gradient2 is similar to Gradient, but instead of using observed minimum RTT as the base, it uses and exponentially smoothed average RTT.

## Backpressure Tuning

The goal of backpressure is to keep the processing latency low.
The processing latency is calculated as the time between the command is written to the event stream until it is processed.
Hence to see how backpressure behaves you can run a benchmark on your cluster and observe
the following metrics.

* `zeebe_stream_processor_latency_bucket`
* `zeebe_dropped_request_count_total`
* `zeebe_received_request_count_total`
* `zeebe_backpressure_requests_limit`

You may want to run the benchmark with different load
1. With low load - where the number of requests send per second is low.
2. With high load - where the number of requests sent per second is above what zeebe can process within a reasonable latency.

If the value of the limit is small, the processing latency will be small but the number of rejected requests may be high.
If the value of the limit is large, less requests may be rejected (depending on the request rate),
but the processing latency may increase.

When using "fixed limit", you can run the benchmark with different values for the limit.
You can then determine a suitable value for a limit for which the processing latency (`zeebe_stream_processor_latency_bucket`) is within the desired latency.

When using "AIMD", you can configure a *requestTimeout* which corresponds to a desired latency.
Note that during high load "AIMD" can lead to a processing latency two times more than the configured *requestTimeout*.
It is also recommended to configure a *minLimit* to prevent the limit from aggressively dropping during constant high load.

When using "Vegas", you cannot configure the backpressure to a desired latency.
Instead Vegas tries to keep the RTT as low as possible based on the observed minimum RTT.

Similar to "Vegas", you cannot configure the desired latency in "Gradient" and "Gradient2".
They calculated the limit based on the gradient of observed RTT from the expected RTT.
Higher the value of *rttTolerance*, higher deviations are tolerated that results in higher values for limit.

If a lot of requests are rejected due to backpressure, it might indicate that the processing capacity of the cluster is not enough to handle the expected throughput.
If this is the expected workload, then you might consider a different configuration for the cluster such as provisioning more resources and, increasing the number of nodes and partitions.








