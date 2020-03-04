# Network Ports


The broker cluster sits behind the gRPC Gateway, which handles all requests from clients/workers and forwards events to brokers.

## Gateway 
The gateway needs to receive communication on 
- `zeebe.gateway.network.port: 26500` from clients/workers, and 
- `zeebe.gateway.cluster.contactPoint: 127.0.0.1:26502` from brokers 

The relevant [configuration](/appendix/gateway-config-template.md) settings are:
```
Config file
    zeebe:
      gateway:
        network:
          port: 26500
        cluster:
          contactPoint: 127.0.0.1:26502
        

Environment Variables
  ZEEBE_GATEWAY_CLUSTER_NETWORK_PORT = 26500
  ZEEBE_GATEWAY_CLUSTER_CONTACTPOINT = 127.0.0.1:26502  
```

## Broker
The broker needs to receive communication from the gateway and from other brokers. It also exposes a port for monitoring.
 
- `zeebe.broker.network.commandApi.port: 26501`: Gateway-to-broker communication, using an internal SBE (Simple Binary Encoding) protocol. This is the Command API port. This should be exposed to the gateway. 
- `zeebe.broker.network.internalApi.port: 26502`: Inter-broker clustering using the Gossip and Raft protocols for partition replication, broker elections, topology sharing, and message subscriptions. This should be exposed to other brokers and the gateway.
- `zeebe.broker.network.monitoringApi.port: 9600`: Metrics and Readiness Probe. Prometheus metrics are exported on the route `/metrics`. There is a readiness probe on `/ready`.

The relevant [configuration](/appendix/broker-config-template.md) settings are:
```
Config file
    zeebe:
      broker:
        network:
          commandAPI:
            port: 26501
          internalAPI:
            port: 26502
          monitoringApi
            port: 9600

Environment Variables
  ZEEBE_BROKER_NETWORK_COMMANDAPI_PORT = 26501
  ZEEBE_BROKER_NETWORK_INTERNALAPI_PORT = 26501
  ZEEBE_BROKER_NETWORK_MONITOIRNGAPI_PORT = 26501
```