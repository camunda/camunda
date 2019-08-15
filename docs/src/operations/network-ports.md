# Network Ports

The broker cluster sits behind the gRPC Gateway, which handles all requests from workers. 
The gateway needs to receive communication from clients on port 26500 and the brokers on port 26502. The brokers need to receive communication from the gateway on port 26501 and from other brokers on port 26502.

**9600** - Metrics and Readiness Probe. Prometheus metrics are exported on the route /metrics. There is a readiness probe on /ready.

**26500** - gRPC Gateway. This is the Client API port. This should be exposed to clients external to the cluster.

**26501** - Gateway-to-broker communication, using an internal SBE (Simple Binary Encoding) protocol. This is the Command API port. This should be exposed to the gateway.

**26502** - Inter-broker clustering using the Gossip and Raft protocols for partition replication, broker elections, topology sharing, and message subscriptions. This should be exposed to other brokers and the gateway.