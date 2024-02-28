# Batch operation performance test

Prerequisites: Elasticsearch with dataset under test is running (both Zeebe + Operate data)

This test:
1. Mocks Zeebe answers
2. Create big amount of operations to be executed (cancel process instance and resolve incidents)
2. Execute operations and print out the duration
