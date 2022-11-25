# Test for backup and restore of the data

This test:
1. Runs Elasticsearch, Zeebe and Operate 
2. Generate dataset 1
3. Make backup of Operate data
4. Add more data: dataset 2
5. Stop Operate
6. Restore backup
7. Start Operate
8. Assert dataset 1