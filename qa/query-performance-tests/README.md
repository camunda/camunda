# Query performance test

Prerequisites:
1. Elasticserch with dataset under test is running
2. Operate is running

This test:
1. Reads all json files from "queries" folder and parse queries.
2. Runs queries one by one and assert that execution time is less than configured threshold (3 seconds by default).

To ignore the query put `ignore` instruction in query json:

```
[
  {
    "ignore": "OPE-537",
    ...
   }
]
```  