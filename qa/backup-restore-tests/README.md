# Test for backup and restore of the data

This test:
1. Runs Elasticsearch, Zeebe and Tasklist 
2. Generate dataset 1
3. Make backup of Tasklist data
4. Add more data: dataset 2
5. Stop Tasklist
6. Restore backup
7. Start Tasklist
8. Assert dataset 1