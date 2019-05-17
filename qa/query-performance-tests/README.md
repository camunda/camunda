# Query performance test

Prerequisites:
1. Elasticserch with dataset under test is running
2. Operate is running

This test:
runs queries one by one and assert that execution time is less than configured threshold (5s by default).  