# Parallel Gateway (AND)

![workflow](/bpmn-workflows/parallel-gateway-example.png)

A parallel gateway is only activated when a token has arrived on each of its incoming sequence flows. Once activated, all of the outgoing sequence flows are taken. So in the case of multiple outgoing sequence flows the branches are executed concurrently. Execution progresses independently until a synchronizing element is reached, for example, another merging parallel gateway.

BPMN Modeler: [Click Here](/bpmn-modeler/gateways.html#parallel-gateway)
