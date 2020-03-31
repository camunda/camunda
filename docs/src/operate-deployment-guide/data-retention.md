# Data Retention

## How the data is stored and archived

Operate imports data from Zeebe and stores it in Elasticsearch indices with defined prefix (default: `operate`). Specifically:
 * deployed workflows, including the diagrams
 * the state of workflow instances, including variables, flow nodes, that were activated within instance execution, incidents etc.
 
It additionally stores some Operate specific data:
 * operations performed by the user
 * list of users
 * technical data, like the state of Zeebe import etc.
 
The data that represents workflow instance state becomes immutable after workflow instance is finished. At this moment the data may be archived, meaning that 
it will be moved to a dated index, e.g. `operate_variables_2020-01-01`, where date represents the date on which given workflow instance was finished.
The same is valid for user operations: after they are finished the related data is moved to dated indices.

> **Note:** All Operate data present in Elasticsearch (from both "main" and dated indices) will be visible from the UI. 

## Data cleanup

In case of intensive Zeebe usage the amount of data can grow significantly with the time, therefore you should think about the data cleanup strategy. Dated indices
may be safely removed from Elasticsearch. "Safely" means here, that only finished workflow instances will be deleted together with all related data, and the rest or the data 
will stay consistent. You can use Elasticsearch Curator or other tools/scripts to delete old data.

> **Attention:** Only indices that contain dates in their suffix may be deleted.
