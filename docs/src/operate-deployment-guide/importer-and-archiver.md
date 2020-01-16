# Importer & Archiver.  Scaling Operate

Operate consists of three modules:
 * **Webapp** - contains the UI and operation executor functionality
 * **Importer** - is responsible for importing data from Zeebe 
 * **Archiver** - is responsible for archiving "old" data (finished workflow instances and user operations) (see [Data retention](data-retention.md)).
 
Modules can be run together or separately in any combination and can be scaled. When you run Operate instance, by default, all modules are enabled. 
To disable them you can use following configuration parameters:
```
camunda.operate:
 importerEnabled: true|false (default:true)
 archiverEnabled: true|false (default:true)
 webappEnabled: true|false (default:true)
```

Additionally you can have several importer and archiver nodes to increase throughput. Internally they will spread their work based on Zeebe partitions.

E.g. if your Zeebe runs 10 partitions and you configure 2 importer nodes, they will import data from 5 partitions each.
Each single importer/archiver node must be configured with the use of following configuration parameters:

```
camunda.operate.clusterNode:
  partitionIds: array of Zeebe partition ids, this Importer (or Archiver) node must be responsible for, default: empty array, meaning all partitions data is loaded
  nodeCount: total amount of Importer (or Archiver) nodes in the cluster
  currentNodeId: id of current Importer (or Archiver) node, starting from 0
```

It's enough to configure either `partitionIds` or pair of `nodeCount` and `currentNodeId`. In case you provide `nodeCount` and `currentNodeId`,
each node will automatically guess Zeebe partitions it is responsible for.

>**Note** `nodeCount` always represents the number of nodes of one specific type.

E.g. configuration of the cluster with 1 Webapp node, 2 Importer nodes and 1 Archiver node could look like this:
```
Webapp node

camunda.operate:
  archiverEnabled: false
  importerEnabled: false
  #other configuration...

Importer node #1

camunda.operate:
  archiverEnabled: false
  webappEnabled: false
  clusterNode:
    nodeCount: 2
    currentNodeId: 0
  #other configuration...
  
Importer node #2

camunda.operate:
  archiverEnabled: false
  webappEnabled: false
  clusterNode:
    nodeCount: 2
    currentNodeId: 1
  #other configuration...
  
Archiver node

camunda.operate:
  webappEnabled: false
  importerEnabled: false
  
```

You can further parallelize archiver and(or) importer within one node by using following configuration parameters:
```
camunda.operate:
  archiver.threadsCount: number of threads, in which data will be archived (default: 1)
  importer.threadsCount: number of threads, in which data will be imported (default: 3)
```

>**Note** Parallelization of import and archiving within one node will also happen based on Zeebe partitions, meaning that only configurations with
> (number of nodes) * (threadsCount) <= (total number of Zeebe patitions) will make sense. Too many threads and nodes will still work, but some of them will be idle.

